package ua.co.tensa.modules.discord;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Serializes lifecycle replacement and rolls back to the last validated plan
 * if activation fails. Old and new runtimes are never active concurrently.
 */
final class AtomicRuntimeSlot<P, R> implements AutoCloseable {
    private final Function<P, R> activator;
    private final Consumer<R> deactivator;
    private P plan;
    private R runtime;

    AtomicRuntimeSlot(Function<P, R> activator, Consumer<R> deactivator) {
        this.activator = Objects.requireNonNull(activator, "activator");
        this.deactivator = Objects.requireNonNull(deactivator, "deactivator");
    }

    synchronized void start(P initialPlan) {
        if (runtime != null) {
            return;
        }
        runtime = activator.apply(Objects.requireNonNull(initialPlan, "initialPlan"));
        plan = initialPlan;
    }

    synchronized void replace(P nextPlan) {
        Objects.requireNonNull(nextPlan, "nextPlan");
        P previousPlan = plan;
        stopCurrent();
        try {
            runtime = activator.apply(nextPlan);
            plan = nextPlan;
        } catch (RuntimeException replacementFailure) {
            if (previousPlan != null) {
                try {
                    runtime = activator.apply(previousPlan);
                    plan = previousPlan;
                } catch (RuntimeException rollbackFailure) {
                    plan = null;
                    runtime = null;
                    replacementFailure.addSuppressed(rollbackFailure);
                }
            }
            throw new IllegalStateException(
                    "Runtime replacement failed; previous validated plan was restored",
                    replacementFailure
            );
        }
    }

    synchronized P plan() {
        return plan;
    }

    @Override
    public synchronized void close() {
        stopCurrent();
        plan = null;
    }

    private void stopCurrent() {
        R current = runtime;
        runtime = null;
        if (current != null) {
            deactivator.accept(current);
        }
    }
}
