package ua.co.tensa.modules.runtime;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModuleSchedulerTest {
    @Test
    void cancellingHandleInterruptsItsRunningWorker() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        try (ModuleScheduler scheduler = new ModuleScheduler("cancel", defaults())) {
            ModuleScheduler.Handle handle = scheduler.schedule(ModuleScheduler.job("blocking", () -> {
                started.countDown();
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException expected) {
                    interrupted.countDown();
                }
            }).timeout(Duration.ofSeconds(30)).build());
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            handle.cancel();
            assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void cancellingQueuedJobPreventsItsTaskFromExecuting() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch following = new CountDownLatch(1);
        AtomicInteger cancelledRuns = new AtomicInteger();
        try (ModuleScheduler scheduler = new ModuleScheduler("queued", new ModuleScheduler.Defaults(
                16, 1, 16, Duration.ofSeconds(5), 1, Duration.ZERO, Duration.ZERO, 0))) {
            scheduler.schedule(ModuleScheduler.job("first", () -> {
                started.countDown();
                release.await();
            }).build());
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            ModuleScheduler.Handle cancelled = scheduler.schedule(
                    ModuleScheduler.job("cancelled", cancelledRuns::incrementAndGet).build());
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (scheduler.snapshot().workerQueueDepth() == 0 && System.nanoTime() < deadline) {
                Thread.sleep(1);
            }
            assertThat(scheduler.snapshot().workerQueueDepth()).isOne();
            cancelled.cancel();
            scheduler.schedule(ModuleScheduler.job("following", following::countDown).build());
            release.countDown();
            assertThat(following.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(cancelledRuns).hasValue(0);
        } finally {
            release.countDown();
        }
    }

    @Test
    void timedOutAttemptMustExitBeforeRetryStarts() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch retry = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger();
        try (ModuleScheduler scheduler = new ModuleScheduler("overlap", defaults())) {
            scheduler.schedule(ModuleScheduler.job("slow", () -> {
                if (attempts.incrementAndGet() == 1) {
                    started.countDown();
                    boolean done = false;
                    while (!done) {
                        try {
                            release.await();
                            done = true;
                        } catch (InterruptedException expected) {
                            interrupted.countDown();
                        }
                    }
                } else {
                    retry.countDown();
                }
            }).timeout(Duration.ofMillis(100)).attempts(2)
                    .backoff(Duration.ZERO, Duration.ZERO).build());
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(interrupted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(retry.await(150, TimeUnit.MILLISECONDS)).isFalse();
            release.countDown();
            assertThat(retry.await(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            release.countDown();
        }
    }

    @Test
    void throwingConditionIsAccountedForInsteadOfStrandingJob() throws Exception {
        CountDownLatch deadLetter = new CountDownLatch(1);
        try (ModuleScheduler scheduler = new ModuleScheduler("condition", defaults())) {
            scheduler.schedule(ModuleScheduler.job("bad-condition", () -> { })
                    .condition(() -> { throw new IllegalStateException("unavailable"); })
                    .attempts(1).onDeadLetter(ignored -> deadLetter.countDown()).build());
            assertThat(deadLetter.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(scheduler.snapshot().activeJobs()).isZero();
        }
    }

    @Test
    void retriesWithBoundsThenCompletesWithoutDeadLetter() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch completed = new CountDownLatch(1);
        try (ModuleScheduler scheduler = new ModuleScheduler("test", defaults())) {
            scheduler.schedule(ModuleScheduler.job("recover", () -> {
                        if (attempts.incrementAndGet() < 3) {
                            throw new IllegalStateException("transient");
                        }
                        completed.countDown();
                    })
                    .attempts(3)
                    .backoff(Duration.ofMillis(5), Duration.ofMillis(10))
                    .build());

            assertThat(completed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(attempts).hasValue(3);
            assertThat(scheduler.snapshot().deadLetters()).isZero();
        }
    }

    @Test
    void conditionPauseResumeScopeCancellationAndDedupeAreLifecycleSafe() throws Exception {
        AtomicBoolean enabled = new AtomicBoolean();
        AtomicInteger runs = new AtomicInteger();
        CountDownLatch firstRun = new CountDownLatch(1);
        try (ModuleScheduler scheduler = new ModuleScheduler("test", defaults())) {
            ModuleScheduler.Handle handle = scheduler.schedule(ModuleScheduler.job("heartbeat", () -> {
                        runs.incrementAndGet();
                        firstRun.countDown();
                    })
                    .scope("communications")
                    .dedupe("heartbeat")
                    .condition(enabled::get)
                    .interval(Duration.ofMillis(20))
                    .build());

            assertThatThrownBy(() -> scheduler.schedule(ModuleScheduler.job("duplicate", () -> { })
                    .dedupe("heartbeat").build()))
                    .isInstanceOf(IllegalStateException.class);
            Thread.sleep(60);
            assertThat(runs).hasValue(0);

            enabled.set(true);
            handle.resume();
            assertThat(firstRun.await(2, TimeUnit.SECONDS)).isTrue();
            handle.pause();
            int pausedAt = runs.get();
            Thread.sleep(70);
            assertThat(runs.get()).isLessThanOrEqualTo(pausedAt + 1);

            scheduler.cancelScope("communications");
            assertThat(handle.isCancelled()).isTrue();
            assertThat(scheduler.snapshot().activeJobs()).isZero();
        }
    }

    @Test
    void timeoutCreatesBoundedDeadLetterAndCloseDropsAllJobs() throws Exception {
        CountDownLatch deadLetter = new CountDownLatch(1);
        ModuleScheduler scheduler = new ModuleScheduler("test", new ModuleScheduler.Defaults(
                1, 1, 1, Duration.ofMillis(20), 1,
                Duration.ofMillis(5), Duration.ofMillis(10), 0.0
        ));
        scheduler.schedule(ModuleScheduler.job("slow", () -> Thread.sleep(5_000))
                .timeout(Duration.ofMillis(20))
                .onDeadLetter(ignored -> deadLetter.countDown())
                .build());

        assertThat(deadLetter.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(scheduler.snapshot().deadLetters()).isOne();
        scheduler.close();
        assertThat(scheduler.snapshot().activeJobs()).isZero();
        assertThatThrownBy(() -> scheduler.schedule(ModuleScheduler.job("late", () -> { }).build()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void intervalRangeChoosesAValidDelayForEveryOccurrence() throws Exception {
        AtomicInteger runs = new AtomicInteger();
        CountDownLatch completed = new CountDownLatch(3);
        try (ModuleScheduler scheduler = new ModuleScheduler("range", defaults())) {
            scheduler.schedule(ModuleScheduler.job("ranged", () -> {
                        runs.incrementAndGet();
                        completed.countDown();
                    })
                    .delay(Duration.ZERO, Duration.ofMillis(5))
                    .interval(Duration.ofMillis(5), Duration.ofMillis(15))
                    .build());

            assertThat(completed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(runs.get()).isGreaterThanOrEqualTo(3);
        }
    }

    private static ModuleScheduler.Defaults defaults() {
        return new ModuleScheduler.Defaults(
                16, 2, 16, Duration.ofSeconds(1), 2,
                Duration.ofMillis(5), Duration.ofMillis(50), 0.0
        );
    }
}
