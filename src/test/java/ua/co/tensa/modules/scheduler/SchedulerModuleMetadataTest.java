package ua.co.tensa.modules.scheduler;

import org.junit.jupiter.api.Test;
import ua.co.tensa.modules.TensaModule;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulerModuleMetadataTest {
    @Test
    void commandSchedulerIsOptionalVisibleAndEnabledByDefault() {
        TensaModule metadata = SchedulerProvider.class.getAnnotation(TensaModule.class);

        assertThat(metadata.id()).isEqualTo("scheduler");
        assertThat(metadata.title()).isEqualTo("Command Scheduler");
        assertThat(metadata.defaultEnabled()).isTrue();
        assertThat(SchedulerModule.ENTRY.id()).isEqualTo("scheduler");
        assertThat(SchedulerModule.ENTRY.required()).isFalse();
    }
}
