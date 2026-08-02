package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;

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

        assertThat(counters.listeners.get()).isOne();
        assertThat(counters.tasks.get()).isOne();
        assertThat(counters.commands.get()).isOne();
        assertThat(counters.botRuntimes.get()).isOne();
        assertThat(counters.maximumBotRuntimes.get()).isOne();
        assertThat(slot.plan()).isEqualTo("v4");

        slot.close();
        assertThat(counters.listeners.get()).isZero();
        assertThat(counters.tasks.get()).isZero();
        assertThat(counters.commands.get()).isZero();
        assertThat(counters.botRuntimes.get()).isZero();
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

    private static final class FakeRuntime {
        private final Counters counters;
        private boolean closed;

        private FakeRuntime(String ignored, Counters counters) {
            this.counters = counters;
            counters.listeners.incrementAndGet();
            counters.tasks.incrementAndGet();
            counters.commands.incrementAndGet();
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
        }
    }

    private static final class Counters {
        private final AtomicInteger listeners = new AtomicInteger();
        private final AtomicInteger tasks = new AtomicInteger();
        private final AtomicInteger commands = new AtomicInteger();
        private final AtomicInteger botRuntimes = new AtomicInteger();
        private final AtomicInteger maximumBotRuntimes = new AtomicInteger();
    }
}
