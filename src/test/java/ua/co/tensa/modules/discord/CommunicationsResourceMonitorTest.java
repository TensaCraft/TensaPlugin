package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommunicationsResourceMonitorTest {
    @Test
    void reportsOnlyHeapPressureTransitionsWithRecoveryHysteresis() {
        CommunicationsResourceMonitor monitor = new CommunicationsResourceMonitor(100, 85, 75);

        assertThat(monitor.evaluate(snapshot(84, 100))).isEqualTo(CommunicationsResourceMonitor.Transition.NONE);
        assertThat(monitor.evaluate(snapshot(86, 100))).isEqualTo(CommunicationsResourceMonitor.Transition.HIGH);
        assertThat(monitor.evaluate(snapshot(90, 100))).isEqualTo(CommunicationsResourceMonitor.Transition.NONE);
        assertThat(monitor.evaluate(snapshot(76, 100))).isEqualTo(CommunicationsResourceMonitor.Transition.NONE);
        assertThat(monitor.evaluate(snapshot(74, 100))).isEqualTo(CommunicationsResourceMonitor.Transition.RECOVERED);
    }

    private static CommunicationsResourceSnapshot snapshot(long used, long maximum) {
        return new CommunicationsResourceSnapshot(used, maximum, used - 100,
                1, 2, 3, 4, 5, 6, 7, 8);
    }
}
