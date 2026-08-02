package ua.co.tensa.modules.discord;

import ua.co.tensa.Message;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class DiscordLinkService implements AutoCloseable {
    enum ResultType {
        LINKED,
        INVALID_CODE,
        EXPIRED_CODE,
        PLAYER_ALREADY_LINKED,
        DISCORD_ALREADY_LINKED,
        ROLE_FAILED,
        UNLINKED,
        NOT_LINKED,
        BUSY,
        FAILED
    }

    record Result(ResultType type, LinkedAccount account) {
        static Result of(ResultType type) {
            return new Result(type, null);
        }
    }

    record IssuedCode(String code, Instant expiresAt, LinkedAccount existingAccount) {
        boolean issued() {
            return code != null;
        }
    }

    private final AtomicLinkStore store;
    private final LinkCodeRegistry codes;
    private final DiscordGateway gateway;
    private final DiscordSettings settings;
    private final ExecutorService executor;
    private final AtomicBoolean closed = new AtomicBoolean();

    DiscordLinkService(AtomicLinkStore store, LinkCodeRegistry codes, DiscordGateway gateway, DiscordSettings settings) {
        this.store = store;
        this.codes = codes;
        this.gateway = gateway;
        this.settings = settings;
        this.executor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(settings.linkExecutorCapacity()),
                runnable -> Thread.ofPlatform().name("tensa-discord-linking").daemon(true).unstarted(runnable),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    IssuedCode issue(UUID playerUuid, String playerName, Instant now) {
        Optional<LinkedAccount> existing = store.findByPlayer(playerUuid);
        if (existing.isPresent()) {
            return new IssuedCode(null, null, existing.get());
        }
        String code = codes.issue(playerUuid, playerName, now);
        return new IssuedCode(code, now.plus(settings.linkCodeTtl()), null);
    }

    CompletableFuture<Optional<LinkedAccount>> status(UUID playerUuid) {
        return submit(() -> store.findByPlayer(playerUuid));
    }

    CompletableFuture<Result> complete(String code, String discordUserId, String discordUserName) {
        return submit(() -> completeBlocking(code, discordUserId, discordUserName));
    }

    CompletableFuture<Result> unlink(UUID playerUuid) {
        return submit(() -> unlinkBlocking(playerUuid));
    }

    void reconcileRoles() {
        submit(() -> {
            for (LinkedAccount account : store.all()) {
                try {
                    await(gateway.assignLinkedRole(account.discordUserId()));
                } catch (Exception e) {
                    Message.warn("Discord linked-role reconciliation failed for one account");
                }
            }
            return null;
        }).exceptionally(ignored -> null);
    }

    private Result completeBlocking(String code, String discordUserId, String discordUserName) {
        LinkCodeRegistry.ConsumeResult consumed = codes.consume(code, Instant.now());
        if (consumed.status() == LinkCodeRegistry.ConsumeStatus.INVALID) {
            return Result.of(ResultType.INVALID_CODE);
        }
        if (consumed.status() == LinkCodeRegistry.ConsumeStatus.EXPIRED) {
            return Result.of(ResultType.EXPIRED_CODE);
        }

        LinkCodeRegistry.PendingLink pending = consumed.pendingLink();
        LinkedAccount account = new LinkedAccount(
                pending.playerUuid(),
                pending.playerName(),
                discordUserId,
                DiscordSanitizer.truncate(DiscordSanitizer.normalize(discordUserName), 80),
                Instant.now()
        );
        try {
            AtomicLinkStore.LinkOutcome outcome = store.link(account);
            if (outcome == AtomicLinkStore.LinkOutcome.ALREADY_LINKED
                    || outcome == AtomicLinkStore.LinkOutcome.PLAYER_LINKED_ELSEWHERE) {
                return Result.of(ResultType.PLAYER_ALREADY_LINKED);
            }
            if (outcome == AtomicLinkStore.LinkOutcome.DISCORD_LINKED_ELSEWHERE) {
                return Result.of(ResultType.DISCORD_ALREADY_LINKED);
            }
            try {
                awaitGatewayReadyForRole();
                await(gateway.assignLinkedRole(discordUserId));
                return new Result(ResultType.LINKED, account);
            } catch (Exception roleFailure) {
                store.unlink(account.playerUuid());
                return Result.of(ResultType.ROLE_FAILED);
            }
        } catch (IOException e) {
            return Result.of(ResultType.FAILED);
        }
    }

    private Result unlinkBlocking(UUID playerUuid) {
        Optional<LinkedAccount> existing = store.findByPlayer(playerUuid);
        if (existing.isEmpty()) {
            return Result.of(ResultType.NOT_LINKED);
        }
        LinkedAccount account = existing.get();
        try {
            awaitGatewayReadyForRole();
            await(gateway.removeLinkedRole(account.discordUserId()));
        } catch (Exception e) {
            return Result.of(ResultType.ROLE_FAILED);
        }
        try {
            store.unlink(playerUuid);
            codes.invalidate(playerUuid);
            return new Result(ResultType.UNLINKED, account);
        } catch (IOException e) {
            try {
                await(gateway.assignLinkedRole(account.discordUserId()));
            } catch (Exception ignored) {
                Message.warn("Discord role compensation failed after a local unlink storage error");
            }
            return Result.of(ResultType.FAILED);
        }
    }

    private void await(CompletableFuture<Void> future) throws Exception {
        future.get(settings.deliveryTimeout().toMillis(), TimeUnit.MILLISECONDS);
    }

    private void awaitGatewayReadyForRole() throws InterruptedException {
        if (settings.linkedRoleId().isBlank()) {
            return;
        }
        long deadline = System.nanoTime() + settings.deliveryTimeout().toNanos();
        while (!closed.get() && !gateway.isReady() && System.nanoTime() < deadline) {
            Thread.sleep(100L);
        }
        if (!gateway.isReady()) {
            throw new IllegalStateException("Discord gateway is unavailable for a role operation");
        }
    }

    private <T> CompletableFuture<T> submit(ThrowingSupplier<T> task) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Discord linking service is closed"));
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    future.complete(task.get());
                } catch (Throwable e) {
                    future.completeExceptionally(e);
                }
            });
        } catch (RejectedExecutionException e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
