package ua.co.tensa.modules.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FirstJoinRegistryTest {

    @TempDir
    Path tempDir;

    @Test
    void markFirstJoinPersistsAcrossReloads() {
        Path file = tempDir.resolve("events").resolve("first-join.yml");
        UUID uuid = UUID.randomUUID();

        try (FirstJoinRegistry registry = new FirstJoinRegistry(file)) {
            FirstJoinRegistry.MarkResult first = registry.markFirstJoin(uuid, "Steve");
            FirstJoinRegistry.MarkResult second = registry.markFirstJoin(uuid, "Steve");

            assertThat(first.firstJoin()).isTrue();
            assertThat(first.firstSeenAt()).isNotBlank();
            assertThat(second.firstJoin()).isFalse();
            assertThat(second.firstSeenAt()).isEqualTo(first.firstSeenAt());
        }

        try (FirstJoinRegistry registry = new FirstJoinRegistry(file)) {
            FirstJoinRegistry.MarkResult afterReload = registry.markFirstJoin(uuid, "Steve");

            assertThat(afterReload.firstJoin()).isFalse();
            assertThat(afterReload.firstSeenAt()).isNotBlank();
        }
    }
}
