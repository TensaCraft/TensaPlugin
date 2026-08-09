package ua.co.tensa.modules.scheduler;

import ua.co.tensa.Message;
import ua.co.tensa.modules.runtime.ModuleScheduler;
import ua.co.tensa.modules.runtime.RuntimeSchedulerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;

final class ScheduledCommandsRuntime implements AutoCloseable {
    private static final String SCOPE = "scheduled-commands";

    private final ModuleScheduler scheduler;
    private final ScheduledCommandDispatcher dispatcher;
    private final IntUnaryOperator randomIndex;
    private final Map<String, AtomicInteger> roundRobin = new ConcurrentHashMap<>();
    private final Map<String, String> failures = new ConcurrentHashMap<>();
    private final int enabledTasks;

    ScheduledCommandsRuntime(ScheduledCommandsPlan plan, ScheduledCommandDispatcher dispatcher) {
        this(plan, dispatcher, bound -> ThreadLocalRandom.current().nextInt(bound),
                RuntimeSchedulerFactory.create("tensa-command-scheduler"));
    }

    ScheduledCommandsRuntime(
            ScheduledCommandsPlan plan,
            ScheduledCommandDispatcher dispatcher,
            IntUnaryOperator randomIndex,
            ModuleScheduler scheduler
    ) {
        this.dispatcher = java.util.Objects.requireNonNull(dispatcher, "dispatcher");
        this.randomIndex = java.util.Objects.requireNonNull(randomIndex, "randomIndex");
        this.scheduler = java.util.Objects.requireNonNull(scheduler, "scheduler");
        int active = 0;
        try {
            for (ScheduledCommandsPlan.Task task : plan.tasks()) {
                if (!task.enabled()) {
                    continue;
                }
                active++;
                scheduler.schedule(ModuleScheduler.job("scheduled-command-" + task.id(), () -> execute(task))
                        .scope(SCOPE)
                        .dedupe("scheduled-command-" + task.id())
                        .delay(task.initialDelay().minimum(), task.initialDelay().maximum())
                        .interval(task.interval().minimum(), task.interval().maximum())
                        .attempts(1)
                        .onDeadLetter(failure -> recordFailure(task.id(), failure))
                        .build());
            }
        } catch (RuntimeException failure) {
            scheduler.close();
            throw failure;
        }
        this.enabledTasks = active;
    }

    Snapshot snapshot() {
        ModuleScheduler.Snapshot schedulerSnapshot = scheduler.snapshot();
        return new Snapshot(enabledTasks, schedulerSnapshot.activeJobs(), schedulerSnapshot.closed());
    }

    @Override
    public void close() {
        scheduler.close();
        roundRobin.clear();
        failures.clear();
    }

    private void execute(ScheduledCommandsPlan.Task task) {
        Throwable firstFailure = null;
        for (String command : select(task)) {
            try {
                CompletableFuture<Boolean> result = dispatcher.dispatch(command);
                if (result == null) {
                    if (firstFailure == null) {
                        firstFailure = new IllegalStateException("Dispatcher returned no result");
                    }
                    continue;
                }
                if (!Boolean.TRUE.equals(result.get())) {
                    if (firstFailure == null) {
                        firstFailure = new CommandRejectedException();
                    }
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Scheduled command execution was interrupted", interrupted);
            } catch (java.util.concurrent.ExecutionException | RuntimeException failure) {
                if (firstFailure == null) {
                    firstFailure = failure;
                }
            }
        }
        if (firstFailure != null) {
            Throwable unwrapped = unwrap(firstFailure);
            if (unwrapped instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            throw new IllegalStateException("Scheduled command dispatch failed", unwrapped);
        }
        recordRecovery(task.id());
    }

    private List<String> select(ScheduledCommandsPlan.Task task) {
        List<String> commands = task.commands();
        return switch (task.mode()) {
            case ALL -> commands;
            case RANDOM -> List.of(commands.get(index(commands.size())));
            case SHUFFLE -> shuffled(commands);
            case ROUND_ROBIN -> {
                AtomicInteger cursor = roundRobin.computeIfAbsent(task.id(), ignored -> new AtomicInteger());
                int selected = cursor.getAndUpdate(current -> current == Integer.MAX_VALUE ? 0 : current + 1);
                yield List.of(commands.get(Math.floorMod(selected, commands.size())));
            }
        };
    }

    private List<String> shuffled(List<String> source) {
        List<String> shuffled = new ArrayList<>(source);
        for (int current = shuffled.size() - 1; current > 0; current--) {
            int selected = index(current + 1);
            String value = shuffled.get(current);
            shuffled.set(current, shuffled.get(selected));
            shuffled.set(selected, value);
        }
        return List.copyOf(shuffled);
    }

    private int index(int bound) {
        int selected = randomIndex.applyAsInt(bound);
        if (selected < 0 || selected >= bound) {
            throw new IllegalStateException("Random source returned an out-of-range index");
        }
        return selected;
    }

    private void recordFailure(String taskId, Throwable failure) {
        Throwable unwrapped = unwrap(failure);
        String failureClass = unwrapped.getClass().getSimpleName();
        if (!failureClass.equals(failures.put(taskId, failureClass))) {
            Message.warn("scheduler task=" + taskId + " transition=dispatch-failed failure=" + failureClass);
        }
    }

    private void recordRecovery(String taskId) {
        if (failures.remove(taskId) != null) {
            Message.info("scheduler task=" + taskId + " transition=recovered");
        }
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    record Snapshot(int configuredTasks, int activeJobs, boolean closed) {
    }

    private static final class CommandRejectedException extends RuntimeException {
    }
}
