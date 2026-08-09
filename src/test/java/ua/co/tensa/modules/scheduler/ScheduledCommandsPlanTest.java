package ua.co.tensa.modules.scheduler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.co.tensa.Tensa;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduledCommandsPlanTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsFixedAndRandomIntervalsAndNormalizesConsoleCommands() throws Exception {
        Tensa.pluginPath = tempDir;
        Path directory = Files.createDirectories(tempDir.resolve("scheduler"));
        Files.writeString(directory.resolve("config.yml"), """
                config_version: 1
                tasks:
                  announcements:
                    enabled: true
                    initial_delay: 15s
                    interval: 10m..20m
                    mode: random
                    commands:
                      - /alert First
                      - alert Second
                  maintenance:
                    enabled: true
                    interval: 1h
                    mode: round_robin
                    commands:
                      - status
                """);

        ScheduledCommandsConfig config = new ScheduledCommandsConfig();
        config.reloadCfg();
        ScheduledCommandsPlan plan = ScheduledCommandsPlan.from(config.adapter());

        assertThat(plan.tasks()).hasSize(2);
        ScheduledCommandsPlan.Task announcements = plan.tasks().getFirst();
        assertThat(announcements.mode()).isEqualTo(ScheduledCommandsPlan.Mode.RANDOM);
        assertThat(announcements.interval().minimum()).hasMinutes(10);
        assertThat(announcements.interval().maximum()).hasMinutes(20);
        assertThat(announcements.commands()).containsExactly("alert First", "alert Second");
    }

    @Test
    void generatesAnInertDocumentedExampleForNewInstallations() throws Exception {
        Tensa.pluginPath = tempDir;

        ScheduledCommandsConfig config = new ScheduledCommandsConfig();
        config.reloadCfg();
        ScheduledCommandsPlan plan = ScheduledCommandsPlan.from(config.adapter());

        assertThat(tempDir.resolve("scheduler/config.yml")).exists();
        assertThat(plan.tasks()).singleElement().satisfies(task -> {
            assertThat(task.id()).isEqualTo("announcements");
            assertThat(task.enabled()).isFalse();
            assertThat(task.mode()).isEqualTo(ScheduledCommandsPlan.Mode.RANDOM);
        });
    }

    @Test
    void rejectsTyposFutureVersionsUnsafeIntervalsAndNonStringCommands() throws Exception {
        assertInvalid("""
                config_version: 2
                tasks: {}
                """, "newer than supported");
        assertInvalid("""
                config_version: 1
                tasks:
                  spam:
                    enabled: true
                    interval: 100ms
                    mode: all
                    commands: [say no]
                """, "must be a duration");
        assertInvalid("""
                config_version: 1
                tasks:
                  typo:
                    enabled: true
                    interval: 1m
                    modes: random
                    commands: [say no]
                """, "unknown keys");
        assertInvalid("""
                config_version: 1
                tasks:
                  invalid:
                    enabled: true
                    interval: 1m
                    commands: [123]
                """, "commands must be strings");
    }

    private void assertInvalid(String yaml, String message) throws Exception {
        Path caseDirectory = Files.createTempDirectory(tempDir, "case-");
        Tensa.pluginPath = caseDirectory;
        Files.createDirectories(caseDirectory.resolve("scheduler"));
        Files.writeString(caseDirectory.resolve("scheduler/config.yml"), yaml);
        ScheduledCommandsConfig config = new ScheduledCommandsConfig();
        config.reloadCfg();

        assertThatThrownBy(() -> ScheduledCommandsPlan.from(config.adapter()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(message);
    }
}
