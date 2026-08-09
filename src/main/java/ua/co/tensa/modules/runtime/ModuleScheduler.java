package ua.co.tensa.modules.runtime;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Lifecycle-owned bounded scheduler for module tasks. A job has at most one
 * pending trigger and one running attempt, so reload cannot multiply work.
 */
public final class ModuleScheduler implements AutoCloseable {
    private final Defaults defaults;
    private final ScheduledThreadPoolExecutor timer;
    private final ThreadPoolExecutor workers;
    private final Object lock = new Object();
    private final Map<String, JobState> jobs = new HashMap<>();
    private final Map<String, String> dedupeKeys = new HashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong deadLetters = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();

    public ModuleScheduler(String threadPrefix, Defaults defaults) {
        this.defaults = Objects.requireNonNull(defaults, "defaults").validated();
        String safePrefix = threadPrefix == null || threadPrefix.isBlank() ? "module" : threadPrefix;
        this.timer = new ScheduledThreadPoolExecutor(1,
                runnable -> Thread.ofPlatform().name(safePrefix + "-scheduler").daemon(true).unstarted(runnable));
        this.timer.setRemoveOnCancelPolicy(true);
        this.timer.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        this.timer.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        this.workers = new ThreadPoolExecutor(
                this.defaults.workerThreads(), this.defaults.workerThreads(), 30L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(this.defaults.queueCapacity()),
                runnable -> Thread.ofPlatform().name(safePrefix + "-worker").daemon(true).unstarted(runnable),
                new ThreadPoolExecutor.AbortPolicy()
        );
        this.workers.allowCoreThreadTimeOut(true);
    }

    public static Builder job(String key, CheckedRunnable task) {
        return new Builder(key, task);
    }

    public Handle schedule(JobSpec spec) {
        Objects.requireNonNull(spec, "spec");
        JobState state;
        synchronized (lock) {
            ensureOpen();
            if (jobs.size() >= defaults.maxJobs()) {
                rejected.incrementAndGet();
                throw new RejectedExecutionException("Module scheduler job capacity is full");
            }
            if (jobs.containsKey(spec.key())) {
                throw new IllegalStateException("A scheduler job already uses key " + spec.key());
            }
            if (!spec.dedupeKey().isBlank() && dedupeKeys.containsKey(spec.dedupeKey())) {
                throw new IllegalStateException("A scheduler job already uses dedupe key " + spec.dedupeKey());
            }
            state = new JobState(spec.withDefaults(defaults));
            jobs.put(spec.key(), state);
            if (!spec.dedupeKey().isBlank()) {
                dedupeKeys.put(spec.dedupeKey(), spec.key());
            }
            scheduleTriggerLocked(state, state.spec.initialDelay());
        }
        return state;
    }

    public int cancelScope(String scope) {
        java.util.List<JobState> matching;
        synchronized (lock) {
            matching = jobs.values().stream()
                    .filter(state -> state.spec.scope().equals(scope == null ? "" : scope))
                    .toList();
        }
        matching.forEach(JobState::cancel);
        return matching.size();
    }

    public Snapshot snapshot() {
        synchronized (lock) {
            long paused = jobs.values().stream().filter(state -> state.paused).count();
            long running = jobs.values().stream().filter(state -> state.running).count();
            return new Snapshot(
                    jobs.size(), Math.toIntExact(paused), Math.toIntExact(running),
                    workers.getQueue().size(), deadLetters.get(), rejected.get(), closed.get()
            );
        }
    }

    public int scopeJobs(String scope) {
        String normalized = scope == null ? "" : scope;
        synchronized (lock) {
            return Math.toIntExact(jobs.values().stream()
                    .filter(state -> state.spec.scope().equals(normalized))
                    .count());
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        java.util.List<JobState> active;
        synchronized (lock) {
            active = java.util.List.copyOf(jobs.values());
        }
        active.forEach(JobState::cancel);
        timer.shutdownNow();
        workers.shutdownNow();
        synchronized (lock) {
            jobs.clear();
            dedupeKeys.clear();
        }
    }

    private void trigger(JobState state) {
        synchronized (lock) {
            state.trigger = null;
            if (closed.get() || state.cancelled) {
                return;
            }
            if (state.paused) {
                return;
            }
            if (!state.spec.condition().getAsBoolean()) {
                scheduleNextOccurrenceLocked(state);
                return;
            }
            if (state.running) {
                return;
            }
            state.running = true;
            state.attempt = 1;
        }
        executeAttempt(state);
    }

    private void executeAttempt(JobState state) {
        AtomicBoolean finished = new AtomicBoolean();
        Future<?> worker;
        try {
            worker = workers.submit(() -> {
                Throwable failure = null;
                try {
                    state.spec.task().run();
                } catch (Throwable error) {
                    failure = error;
                }
                if (finished.compareAndSet(false, true)) {
                    attemptFinished(state, failure);
                }
            });
        } catch (RejectedExecutionException full) {
            rejected.incrementAndGet();
            attemptFinished(state, full);
            return;
        }
        Duration timeout = state.spec.timeout();
        state.timeout = timer.schedule(() -> {
            if (finished.compareAndSet(false, true)) {
                worker.cancel(true);
                attemptFinished(state, new java.util.concurrent.TimeoutException(
                        "Scheduler job timed out: " + state.spec.key()));
            }
        }, timeout.toNanos(), TimeUnit.NANOSECONDS);
    }

    private void attemptFinished(JobState state, Throwable failure) {
        Consumer<Throwable> deadLetterConsumer = null;
        Throwable deadLetterFailure = null;
        synchronized (lock) {
            ScheduledFuture<?> timeout = state.timeout;
            state.timeout = null;
            if (timeout != null) {
                timeout.cancel(false);
            }
            if (closed.get() || state.cancelled) {
                state.running = false;
                return;
            }
            if (failure != null && state.attempt < state.spec.maxAttempts()) {
                Duration retryDelay = retryDelay(state.spec, state.attempt);
                state.attempt++;
                state.running = false;
                scheduleAttemptLocked(state, retryDelay);
                return;
            }
            state.running = false;
            if (failure != null) {
                deadLetters.incrementAndGet();
                deadLetterConsumer = state.spec.deadLetter();
                deadLetterFailure = failure;
            }
            if (state.spec.interval().isZero()) {
                removeLocked(state);
            } else {
                scheduleTriggerLocked(state, state.spec.interval());
            }
        }
        if (deadLetterConsumer != null) {
            try {
                deadLetterConsumer.accept(deadLetterFailure);
            } catch (RuntimeException ignored) {
                // A dead-letter handler cannot break scheduler bookkeeping.
            }
        }
    }

    private void scheduleAttemptLocked(JobState state, Duration delay) {
        state.trigger = timer.schedule(() -> {
            synchronized (lock) {
                state.trigger = null;
                if (closed.get() || state.cancelled || state.paused) {
                    return;
                }
                state.running = true;
            }
            executeAttempt(state);
        }, delay.toNanos(), TimeUnit.NANOSECONDS);
    }

    private void scheduleNextOccurrenceLocked(JobState state) {
        if (state.spec.interval().isZero()) {
            removeLocked(state);
        } else {
            scheduleTriggerLocked(state, state.spec.interval());
        }
    }

    private void scheduleTriggerLocked(JobState state, Duration delay) {
        if (closed.get() || state.cancelled || state.trigger != null) {
            return;
        }
        state.trigger = timer.schedule(() -> trigger(state), delay.toNanos(), TimeUnit.NANOSECONDS);
    }

    private void removeLocked(JobState state) {
        jobs.remove(state.spec.key(), state);
        if (!state.spec.dedupeKey().isBlank()) {
            dedupeKeys.remove(state.spec.dedupeKey(), state.spec.key());
        }
    }

    private Duration retryDelay(JobSpec spec, int failedAttempt) {
        long base;
        try {
            base = Math.multiplyExact(spec.backoff().toMillis(), 1L << Math.min(30, failedAttempt - 1));
        } catch (ArithmeticException overflow) {
            base = Long.MAX_VALUE;
        }
        long capped = Math.min(base, spec.maxBackoff().toMillis());
        if (spec.jitter() == 0.0) {
            return Duration.ofMillis(Math.max(0L, capped));
        }
        double factor = 1.0 + java.util.concurrent.ThreadLocalRandom.current()
                .nextDouble(-spec.jitter(), spec.jitter());
        return Duration.ofMillis(Math.max(0L, Math.round(capped * factor)));
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("Module scheduler is closed");
        }
    }

    public interface Handle {
        void pause();
        void resume();
        void cancel();
        boolean isCancelled();
    }

    @FunctionalInterface
    public interface CheckedRunnable {
        void run() throws Exception;
    }

    public record Defaults(
            int maxJobs,
            int workerThreads,
            int queueCapacity,
            Duration timeout,
            int maxAttempts,
            Duration backoff,
            Duration maxBackoff,
            double jitter
    ) {
        private Defaults validated() {
            if (maxJobs < 1 || workerThreads < 1 || queueCapacity < 1 || maxAttempts < 1
                    || timeout == null || timeout.isNegative() || timeout.isZero()
                    || backoff == null || backoff.isNegative()
                    || maxBackoff == null || maxBackoff.compareTo(backoff) < 0
                    || jitter < 0.0 || jitter > 1.0) {
                throw new IllegalArgumentException("Invalid module scheduler defaults");
            }
            return this;
        }
    }

    public record Snapshot(
            int activeJobs,
            int pausedJobs,
            int runningJobs,
            int workerQueueDepth,
            long deadLetters,
            long rejected,
            boolean closed
    ) {
    }

    public static final class Builder {
        private final String key;
        private final CheckedRunnable task;
        private String scope = "default";
        private String dedupeKey = "";
        private BooleanSupplier condition = () -> true;
        private Duration initialDelay = Duration.ZERO;
        private Duration interval = Duration.ZERO;
        private Duration timeout;
        private int maxAttempts;
        private Duration backoff;
        private Duration maxBackoff;
        private Double jitter;
        private Consumer<Throwable> deadLetter = ignored -> { };

        private Builder(String key, CheckedRunnable task) {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Scheduler job key must not be blank");
            }
            this.key = key;
            this.task = Objects.requireNonNull(task, "task");
        }

        public Builder scope(String value) { scope = value == null ? "" : value; return this; }
        public Builder dedupe(String value) { dedupeKey = value == null ? "" : value; return this; }
        public Builder condition(BooleanSupplier value) { condition = Objects.requireNonNull(value); return this; }
        public Builder delay(Duration value) { initialDelay = nonNegative(value, "delay"); return this; }
        public Builder interval(Duration value) { interval = nonNegative(value, "interval"); return this; }
        public Builder timeout(Duration value) { timeout = positive(value, "timeout"); return this; }
        public Builder attempts(int value) { maxAttempts = value; return this; }
        public Builder backoff(Duration initial, Duration maximum) {
            backoff = nonNegative(initial, "backoff");
            maxBackoff = nonNegative(maximum, "maxBackoff");
            return this;
        }
        public Builder jitter(double value) { jitter = value; return this; }
        public Builder onDeadLetter(Consumer<Throwable> value) { deadLetter = Objects.requireNonNull(value); return this; }

        public JobSpec build() {
            if (maxAttempts < 0 || jitter != null && (jitter < 0.0 || jitter > 1.0)
                    || backoff != null && maxBackoff.compareTo(backoff) < 0) {
                throw new IllegalArgumentException("Invalid scheduler job override");
            }
            return new JobSpec(key, scope, dedupeKey, condition, task, initialDelay, interval,
                    timeout, maxAttempts, backoff, maxBackoff, jitter, deadLetter);
        }

        private static Duration nonNegative(Duration value, String name) {
            if (value == null || value.isNegative()) {
                throw new IllegalArgumentException(name + " must not be negative");
            }
            return value;
        }

        private static Duration positive(Duration value, String name) {
            if (value == null || value.isNegative() || value.isZero()) {
                throw new IllegalArgumentException(name + " must be positive");
            }
            return value;
        }
    }

    public record JobSpec(
            String key,
            String scope,
            String dedupeKey,
            BooleanSupplier condition,
            CheckedRunnable task,
            Duration initialDelay,
            Duration interval,
            Duration timeout,
            int maxAttempts,
            Duration backoff,
            Duration maxBackoff,
            Double jitter,
            Consumer<Throwable> deadLetter
    ) {
        private JobSpec withDefaults(Defaults defaults) {
            return new JobSpec(key, scope, dedupeKey, condition, task, initialDelay, interval,
                    timeout == null ? defaults.timeout() : timeout,
                    maxAttempts == 0 ? defaults.maxAttempts() : maxAttempts,
                    backoff == null ? defaults.backoff() : backoff,
                    maxBackoff == null ? defaults.maxBackoff() : maxBackoff,
                    jitter == null ? defaults.jitter() : jitter,
                    deadLetter);
        }
    }

    private final class JobState implements Handle {
        private final JobSpec spec;
        private boolean paused;
        private boolean running;
        private boolean cancelled;
        private int attempt;
        private ScheduledFuture<?> trigger;
        private ScheduledFuture<?> timeout;

        private JobState(JobSpec spec) {
            this.spec = spec;
        }

        @Override
        public void pause() {
            synchronized (lock) {
                if (cancelled) return;
                paused = true;
                if (trigger != null) {
                    trigger.cancel(false);
                    trigger = null;
                }
            }
        }

        @Override
        public void resume() {
            synchronized (lock) {
                if (cancelled || closed.get()) return;
                paused = false;
                if (!running && trigger == null) {
                    scheduleTriggerLocked(this, Duration.ZERO);
                }
            }
        }

        @Override
        public void cancel() {
            synchronized (lock) {
                if (cancelled) return;
                cancelled = true;
                if (trigger != null) trigger.cancel(false);
                if (timeout != null) timeout.cancel(false);
                trigger = null;
                timeout = null;
                removeLocked(this);
            }
        }

        @Override
        public boolean isCancelled() {
            synchronized (lock) {
                return cancelled;
            }
        }
    }
}
