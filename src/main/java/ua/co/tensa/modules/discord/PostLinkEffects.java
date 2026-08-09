package ua.co.tensa.modules.discord;

import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.RateLimitedException;
import ua.co.tensa.modules.runtime.ModuleScheduler;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded best-effort effects that run only after a durable link commit. */
final class PostLinkEffects implements AutoCloseable {
    private static final String SCOPE = "discord-post-link";

    @FunctionalInterface
    interface Announcer {
        void announce(LinkedAccount account);
    }

    private final DiscordGateway gateway;
    private final Announcer announcer;
    private final boolean nicknameSync;
    private final int capacity;
    private final int nicknameAttempts;
    private final Duration retryBaseDelay;
    private final CommunicationsMetrics metrics;
    private final ModuleScheduler scheduler;
    private final boolean ownsScheduler;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger pending = new AtomicInteger();

    PostLinkEffects(
            DiscordGateway gateway,
            Announcer announcer,
            boolean nicknameSync,
            int capacity,
            int nicknameAttempts,
            Duration retryBaseDelay,
            CommunicationsMetrics metrics
    ) {
        this(gateway, announcer, nicknameSync, capacity, nicknameAttempts, retryBaseDelay, metrics,
                new ModuleScheduler("tensa-discord-post-link", new ModuleScheduler.Defaults(
                        capacity + 1, 1, capacity, Duration.ofSeconds(12), nicknameAttempts,
                        retryBaseDelay, Duration.ofSeconds(5), 0.1)), true);
    }

    PostLinkEffects(
            DiscordGateway gateway,
            Announcer announcer,
            boolean nicknameSync,
            int capacity,
            int nicknameAttempts,
            Duration retryBaseDelay,
            CommunicationsMetrics metrics,
            ModuleScheduler scheduler
    ) {
        this(gateway, announcer, nicknameSync, capacity, nicknameAttempts, retryBaseDelay, metrics, scheduler, false);
    }

    private PostLinkEffects(
            DiscordGateway gateway,
            Announcer announcer,
            boolean nicknameSync,
            int capacity,
            int nicknameAttempts,
            Duration retryBaseDelay,
            CommunicationsMetrics metrics,
            ModuleScheduler scheduler,
            boolean ownsScheduler
    ) {
        this.gateway = java.util.Objects.requireNonNull(gateway, "gateway");
        this.announcer = java.util.Objects.requireNonNull(announcer, "announcer");
        this.nicknameSync = nicknameSync;
        this.capacity = capacity;
        this.nicknameAttempts = nicknameAttempts;
        this.retryBaseDelay = java.util.Objects.requireNonNull(retryBaseDelay, "retryBaseDelay");
        this.metrics = java.util.Objects.requireNonNull(metrics, "metrics");
        this.scheduler = java.util.Objects.requireNonNull(scheduler, "scheduler");
        this.ownsScheduler = ownsScheduler;
    }

    boolean submit(LinkedAccount account) {
        if (closed.get() || account == null) {
            return false;
        }
        if (pending.incrementAndGet() > capacity + 1) {
            pending.updateAndGet(value -> Math.max(0, value - 1));
            metrics.dropped();
            return false;
        }
        AtomicBoolean announced = new AtomicBoolean();
        AtomicBoolean finished = new AtomicBoolean();
        AtomicInteger attempts = new AtomicInteger();
        String identity = account.playerUuid() + "-" + account.linkedAt().toEpochMilli();
        try {
            scheduler.schedule(ModuleScheduler.job("post-link-" + identity, () -> {
                        if (announced.compareAndSet(false, true)) {
                            try {
                                announcer.announce(account);
                            } catch (RuntimeException failure) {
                                metrics.recordFailure(failure);
                            }
                        }
                        if (!nicknameSync || closed.get()) {
                            finish(finished);
                            return;
                        }
                        int attempt = attempts.incrementAndGet();
                        try {
                            gateway.updateNickname(account.discordUserId(),
                                            DiscordSanitizer.truncate(account.playerName(), 32))
                                    .get(10, TimeUnit.SECONDS);
                            finish(finished);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw interrupted;
                        } catch (Exception failure) {
                            Throwable cause = DiscordDiagnostics.unwrap(failure);
                            metrics.recordFailure(cause);
                            if (!retryable(cause)) {
                                finish(finished);
                                return;
                            }
                            if (attempt < nicknameAttempts) {
                                metrics.retried();
                            }
                            throw failure;
                        }
                    })
                    .scope(SCOPE)
                    .dedupe("post-link-" + identity)
                    .attempts(nicknameSync ? nicknameAttempts : 1)
                    .timeout(Duration.ofSeconds(12))
                    .backoff(retryBaseDelay, Duration.ofSeconds(5))
                    .jitter(0.1)
                    .onDeadLetter(error -> {
                        metrics.recordFailure(error);
                        finish(finished);
                    })
                    .build());
            return true;
        } catch (RuntimeException rejected) {
            finish(finished);
            metrics.dropped();
            metrics.recordFailure(rejected);
            return false;
        }
    }

    int queueDepth() {
        return scheduler.scopeJobs(SCOPE);
    }

    private void finish(AtomicBoolean finished) {
        if (finished.compareAndSet(false, true)) {
            pending.updateAndGet(value -> Math.max(0, value - 1));
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
        scheduler.cancelScope(SCOPE);
        pending.set(0);
        if (ownsScheduler) {
            scheduler.close();
        }
    }
}
