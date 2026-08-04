package ua.co.tensa.modules.discord;

import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.RateLimitedException;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded best-effort effects that run only after a durable link commit. */
final class PostLinkEffects implements AutoCloseable {
    @FunctionalInterface
    interface Announcer {
        void announce(LinkedAccount account);
    }

    private final DiscordGateway gateway;
    private final Announcer announcer;
    private final boolean nicknameSync;
    private final int nicknameAttempts;
    private final Duration retryBaseDelay;
    private final CommunicationsMetrics metrics;
    private final ThreadPoolExecutor executor;
    private final AtomicBoolean closed = new AtomicBoolean();

    PostLinkEffects(
            DiscordGateway gateway,
            Announcer announcer,
            boolean nicknameSync,
            int capacity,
            int nicknameAttempts,
            Duration retryBaseDelay,
            CommunicationsMetrics metrics
    ) {
        this.gateway = java.util.Objects.requireNonNull(gateway, "gateway");
        this.announcer = java.util.Objects.requireNonNull(announcer, "announcer");
        this.nicknameSync = nicknameSync;
        this.nicknameAttempts = nicknameAttempts;
        this.retryBaseDelay = java.util.Objects.requireNonNull(retryBaseDelay, "retryBaseDelay");
        this.metrics = java.util.Objects.requireNonNull(metrics, "metrics");
        this.executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(capacity),
                runnable -> Thread.ofPlatform().name("tensa-discord-post-link").daemon(true).unstarted(runnable),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    boolean submit(LinkedAccount account) {
        if (closed.get() || account == null) {
            return false;
        }
        try {
            executor.execute(() -> run(account));
            return true;
        } catch (java.util.concurrent.RejectedExecutionException rejected) {
            metrics.dropped();
            metrics.recordFailure(rejected);
            return false;
        }
    }

    int queueDepth() {
        return executor.getQueue().size();
    }

    private void run(LinkedAccount account) {
        try {
            announcer.announce(account);
        } catch (RuntimeException failure) {
            metrics.recordFailure(failure);
        }
        if (!nicknameSync || closed.get()) {
            return;
        }
        for (int attempt = 1; attempt <= nicknameAttempts && !closed.get(); attempt++) {
            try {
                CompletableFuture<Void> future = gateway.updateNickname(
                        account.discordUserId(), DiscordSanitizer.truncate(account.playerName(), 32)
                );
                future.get(10, TimeUnit.SECONDS);
                return;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception failure) {
                Throwable cause = DiscordDiagnostics.unwrap(failure);
                metrics.recordFailure(cause);
                if (attempt >= nicknameAttempts || !retryable(cause)) {
                    return;
                }
                metrics.retried();
                try {
                    Thread.sleep(Math.min(5_000L, retryBaseDelay.toMillis() << (attempt - 1)));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    static boolean retryable(Throwable failure) {
        Throwable cause = DiscordDiagnostics.unwrap(failure);
        return cause instanceof RateLimitedException
                || cause instanceof IOException
                || cause instanceof java.util.concurrent.TimeoutException
                || (cause instanceof ErrorResponseException response && response.isServerError());
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        executor.shutdownNow();
        try {
            executor.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            executor.getQueue().clear();
        }
    }
}
