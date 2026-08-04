package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;
import ua.co.tensa.modules.runtime.AtomicRuntimeSlot;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AtomicRuntimeSlotTest {
    @Test
    void repeatedReplacementKeepsExactlyOneListenerTaskCommandAndBotRuntime() {
        Counters counters = new Counters();
        AtomicRuntimeSlot<String, FakeRuntime> slot = new AtomicRuntimeSlot<>(
                plan -> new FakeRuntime(plan, counters),
                FakeRuntime::close
        );

        slot.start("v1");
        slot.replace("v2");
        slot.replace("v3");
        slot.replace("v4");
        slot.replace("v5");
        slot.replace("v6");

        assertThat(counters.listeners.get()).isOne();
        assertThat(counters.tasks.get()).isOne();
        assertThat(counters.commands.get()).isOne();
        assertThat(counters.botRuntimes.get()).isOne();
        assertThat(counters.retrySchedulers.get()).isOne();
        assertThat(counters.replyDeletionSchedulers.get()).isOne();
        assertThat(counters.postLinkWorkers.get()).isOne();
        assertThat(counters.maximumBotRuntimes.get()).isOne();
        assertThat(slot.plan()).isEqualTo("v6");

        slot.close();
        assertThat(counters.listeners.get()).isZero();
        assertThat(counters.tasks.get()).isZero();
        assertThat(counters.commands.get()).isZero();
        assertThat(counters.botRuntimes.get()).isZero();
        assertThat(counters.retrySchedulers.get()).isZero();
        assertThat(counters.replyDeletionSchedulers.get()).isZero();
        assertThat(counters.postLinkWorkers.get()).isZero();
    }

    @Test
    void failedReplacementRestoresPreviousValidatedPlan() {
        Counters counters = new Counters();
        AtomicRuntimeSlot<String, FakeRuntime> slot = new AtomicRuntimeSlot<>(plan -> {
            if ("broken".equals(plan)) {
                throw new IllegalStateException("activation failed");
            }
            return new FakeRuntime(plan, counters);
        }, FakeRuntime::close);
        slot.start("stable");

        assertThatThrownBy(() -> slot.replace("broken"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(slot.plan()).isEqualTo("stable");
        assertThat(counters.botRuntimes.get()).isOne();
        assertThat(counters.listeners.get()).isOne();
    }

    @Test
    void failedRollbackIsReportedWithoutClaimingTheOldRuntimeWasRestored() {
        AtomicInteger activations = new AtomicInteger();
        AtomicRuntimeSlot<String, String> slot = new AtomicRuntimeSlot<>(plan -> {
            if ("stable".equals(plan) && activations.incrementAndGet() == 1) {
                return plan;
            }
            throw new IllegalStateException("activation failed");
        }, ignored -> { });
        slot.start("stable");

        assertThatThrownBy(() -> slot.replace("broken"))
                .hasMessageContaining("rollback failed")
                .hasRootCauseMessage("activation failed");
        assertThat(slot.plan()).isNull();
        assertThat(slot.runtime()).isNull();
    }

    @Test
    void failedDeactivationNeverClaimsThatThePreviousRuntimeSurvived() {
        AtomicRuntimeSlot<String, String> slot = new AtomicRuntimeSlot<>(
                plan -> plan,
                ignored -> { throw new IllegalStateException("deactivation failed"); }
        );
        slot.start("stable");

        assertThatThrownBy(() -> slot.replace("next"))
                .hasMessageContaining("deactivation failed")
                .hasRootCauseMessage("deactivation failed");
        assertThat(slot.plan()).isNull();
        assertThat(slot.runtime()).isNull();
    }

    private static final class FakeRuntime {
        private final Counters counters;
        private boolean closed;

        private FakeRuntime(String ignored, Counters counters) {
            this.counters = counters;
            counters.listeners.incrementAndGet();
            counters.tasks.incrementAndGet();
            counters.commands.incrementAndGet();
            counters.retrySchedulers.incrementAndGet();
            counters.replyDeletionSchedulers.incrementAndGet();
            counters.postLinkWorkers.incrementAndGet();
            int active = counters.botRuntimes.incrementAndGet();
            counters.maximumBotRuntimes.accumulateAndGet(active, Math::max);
        }

        private void close() {
            if (closed) return;
            closed = true;
            counters.listeners.decrementAndGet();
            counters.tasks.decrementAndGet();
            counters.commands.decrementAndGet();
            counters.botRuntimes.decrementAndGet();
            counters.retrySchedulers.decrementAndGet();
            counters.replyDeletionSchedulers.decrementAndGet();
            counters.postLinkWorkers.decrementAndGet();
        }
    }

    private static final class Counters {
        private final AtomicInteger listeners = new AtomicInteger();
        private final AtomicInteger tasks = new AtomicInteger();
        private final AtomicInteger commands = new AtomicInteger();
        private final AtomicInteger botRuntimes = new AtomicInteger();
        private final AtomicInteger maximumBotRuntimes = new AtomicInteger();
        private final AtomicInteger retrySchedulers = new AtomicInteger();
        private final AtomicInteger replyDeletionSchedulers = new AtomicInteger();
        private final AtomicInteger postLinkWorkers = new AtomicInteger();
    }
}
