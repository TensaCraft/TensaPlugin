package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BackendStatusDeduplicatorTest {
    @Test
    void debouncesTransitionsDeduplicatesStableStateAndReportsRecovery() {
        BackendStatusDeduplicator states = new BackendStatusDeduplicator(Duration.ofSeconds(10), 2, 8);
        Instant start = Instant.parse("2026-08-02T09:00:00Z");

        assertThat(states.observe("aero", true, start)).isEmpty();
        assertThat(states.observe("aero", false, start.plusSeconds(1))).isEmpty();
        assertThat(states.observe("aero", false, start.plusSeconds(9))).isEmpty();
        assertThat(states.observe("aero", false, start.plusSeconds(11)))
                .contains(BackendStatusDeduplicator.Transition.UNAVAILABLE);
        assertThat(states.observe("aero", false, start.plusSeconds(30))).isEmpty();
        assertThat(states.observe("aero", true, start.plusSeconds(31))).isEmpty();
        assertThat(states.observe("aero", true, start.plusSeconds(42)))
                .contains(BackendStatusDeduplicator.Transition.RECOVERED);
        assertThat(states.observe("aero", true, start.plusSeconds(60))).isEmpty();
    }

    @Test
    void boundsBackendState() {
        BackendStatusDeduplicator states = new BackendStatusDeduplicator(Duration.ZERO, 1, 2);
        Instant now = Instant.parse("2026-08-02T09:00:00Z");

        states.observe("one", true, now);
        states.observe("two", true, now);
        states.observe("three", true, now);

        assertThat(states.size()).isEqualTo(2);
    }
}
