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
