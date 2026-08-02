package ua.co.tensa.modules.runtime;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Serializes lifecycle replacement and rolls back to the last validated plan
 * if activation fails. Old and new runtimes are never active concurrently.
 */
public final class AtomicRuntimeSlot<P, R> implements AutoCloseable {
    private final Function<P, R> activator;
    private final Consumer<R> deactivator;
    private P plan;
    private R runtime;

    public AtomicRuntimeSlot(Function<P, R> activator, Consumer<R> deactivator) {
        this.activator = Objects.requireNonNull(activator, "activator");
        this.deactivator = Objects.requireNonNull(deactivator, "deactivator");
    }

    public synchronized void start(P initialPlan) {
        if (runtime != null) {
            return;
        }
        runtime = activator.apply(Objects.requireNonNull(initialPlan, "initialPlan"));
        plan = initialPlan;
    }

    public synchronized void replace(P nextPlan) {
        Objects.requireNonNull(nextPlan, "nextPlan");
        P previousPlan = plan;
        try {
            stopCurrent();
        } catch (RuntimeException deactivationFailure) {
            plan = null;
            runtime = null;
            throw new RuntimeReplacementException(
                    "Runtime deactivation failed; the runtime is stopped or partially stopped",
                    deactivationFailure,
                    false
            );
        }
        try {
            runtime = activator.apply(nextPlan);
            plan = nextPlan;
        } catch (RuntimeException replacementFailure) {
            boolean restored = false;
            if (previousPlan != null) {
                try {
                    runtime = activator.apply(previousPlan);
                    plan = previousPlan;
                    restored = true;
                } catch (RuntimeException rollbackFailure) {
                    plan = null;
                    runtime = null;
                    replacementFailure.addSuppressed(rollbackFailure);
                }
            }
            throw new RuntimeReplacementException(
                    previousPlan == null
                            ? "Runtime activation failed"
                            : restored
                                    ? "Runtime replacement failed; previous validated plan was restored"
                                    : "Runtime replacement and rollback failed; runtime is stopped",
                    replacementFailure,
                    restored
            );
        }
    }

    public synchronized P plan() {
        return plan;
    }

    public synchronized R runtime() {
        return runtime;
    }

    @Override
    public synchronized void close() {
        try {
            stopCurrent();
        } finally {
            plan = null;
            runtime = null;
        }
    }

    private void stopCurrent() {
        R current = runtime;
        runtime = null;
        if (current != null) {
            deactivator.accept(current);
        }
    }
}
