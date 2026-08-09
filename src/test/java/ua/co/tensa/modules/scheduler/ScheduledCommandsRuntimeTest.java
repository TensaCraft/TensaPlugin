package ua.co.tensa.modules.scheduler;

import org.junit.jupiter.api.Test;
import ua.co.tensa.modules.runtime.AtomicRuntimeSlot;
import ua.co.tensa.modules.runtime.ModuleScheduler;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduledCommandsRuntimeTest {
    @Test
    void randomModeDispatchesOnlyTheDeterministicallySelectedCommand() throws Exception {
        List<String> dispatched = new CopyOnWriteArrayList<>();
        CountDownLatch first = new CountDownLatch(1);
        ScheduledCommandsPlan plan = plan("random", List.of("first", "second", "third"), Duration.ofSeconds(1));

        try (ScheduledCommandsRuntime runtime = runtime(plan, command -> {
            dispatched.add(command);
            first.countDown();
            return CompletableFuture.completedFuture(true);
        }, bound -> bound - 1)) {
            assertThat(first.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(dispatched).containsExactly("third");
            assertThat(runtime.snapshot().activeJobs()).isOne();
        }
    }

    @Test
    void roundRobinDispatchesCommandsInOrderAcrossOccurrences() throws Exception {
        List<String> dispatched = new CopyOnWriteArrayList<>();
        CountDownLatch firstThree = new CountDownLatch(3);
        ScheduledCommandsPlan plan = plan("round_robin", List.of("one", "two"), Duration.ofMillis(20));

        try (ScheduledCommandsRuntime runtime = runtime(plan, command -> {
            dispatched.add(command);
            firstThree.countDown();
            return CompletableFuture.completedFuture(true);
        }, ignored -> 0)) {
            assertThat(firstThree.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(dispatched).startsWith("one", "two", "one");
        }
    }

    @Test
    void allModeWaitsForEachCommandBeforeDispatchingTheNext() throws Exception {
        List<String> dispatched = new CopyOnWriteArrayList<>();
        CompletableFuture<Boolean> firstResult = new CompletableFuture<>();
        CountDownLatch firstDispatched = new CountDownLatch(1);
        CountDownLatch secondDispatched = new CountDownLatch(1);
        ScheduledCommandsPlan plan = plan("all", List.of("first", "second"), Duration.ofSeconds(1));

        try (ScheduledCommandsRuntime runtime = runtime(plan, command -> {
            dispatched.add(command);
            if ("first".equals(command)) {
                firstDispatched.countDown();
                return firstResult;
            }
            secondDispatched.countDown();
            return CompletableFuture.completedFuture(true);
        }, ignored -> 0)) {
            assertThat(firstDispatched.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(dispatched).containsExactly("first");

            firstResult.complete(true);
            assertThat(secondDispatched.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(dispatched).containsExactly("first", "second");
        }
    }

    @Test
    void shuffleModeDispatchesEveryCommandExactlyOnceInShuffledOrder() throws Exception {
        List<String> dispatched = new CopyOnWriteArrayList<>();
        CountDownLatch all = new CountDownLatch(3);
        ScheduledCommandsPlan plan = plan("shuffle", List.of("one", "two", "three"), Duration.ofSeconds(1));

        try (ScheduledCommandsRuntime runtime = runtime(plan, command -> {
            dispatched.add(command);
            all.countDown();
            return CompletableFuture.completedFuture(true);
        }, ignored -> 0)) {
            assertThat(all.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(dispatched).containsExactly("two", "three", "one");
        }
    }

    @Test
    void fiveAtomicReloadsLeaveOnlyTheNewestRuntimeActive() {
        AtomicRuntimeSlot<ScheduledCommandsPlan, ScheduledCommandsRuntime> slot = new AtomicRuntimeSlot<>(
                plan -> runtime(plan, ignored -> CompletableFuture.completedFuture(true), ignored -> 0),
                ScheduledCommandsRuntime::close
        );
        ScheduledCommandsPlan plan = plan("all", List.of("status"), Duration.ofSeconds(1));
        slot.start(plan);

        for (int reload = 0; reload < 5; reload++) {
            ScheduledCommandsRuntime previous = slot.runtime();
            slot.replace(plan);
            assertThat(previous.snapshot().closed()).isTrue();
            assertThat(slot.runtime().snapshot().activeJobs()).isOne();
        }

        ScheduledCommandsRuntime finalRuntime = slot.runtime();
        slot.close();
        assertThat(finalRuntime.snapshot().closed()).isTrue();
    }

    private static ScheduledCommandsPlan plan(String mode, List<String> commands, Duration interval) {
        ScheduledCommandsPlan.DurationRange zero =
                new ScheduledCommandsPlan.DurationRange(Duration.ZERO, Duration.ZERO);
        ScheduledCommandsPlan.DurationRange every =
                new ScheduledCommandsPlan.DurationRange(interval, interval);
        ScheduledCommandsPlan.Task task = new ScheduledCommandsPlan.Task(
                "test", true, zero, every, ScheduledCommandsPlan.Mode.valueOf(mode.toUpperCase()), commands
        );
        return new ScheduledCommandsPlan(List.of(task));
    }

    private static ScheduledCommandsRuntime runtime(
            ScheduledCommandsPlan plan,
            ScheduledCommandDispatcher dispatcher,
            java.util.function.IntUnaryOperator random
    ) {
        ModuleScheduler scheduler = new ModuleScheduler("scheduled-command-test", new ModuleScheduler.Defaults(
                8, 1, 8, Duration.ofSeconds(1), 1,
                Duration.ZERO, Duration.ZERO, 0.0
        ));
        return new ScheduledCommandsRuntime(plan, dispatcher, random, scheduler);
    }
}
