package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordServerPolicyTest {
    @TempDir
    Path tempDir;

    @Test
    void appliesIncludeExcludeAndPlayerFacingLabels() {
        DiscordSettings settings = DiscordTestSettings.create(tempDir, config -> {
            config.includedServers = List.of("aero", "aero-lobby", "aero-auth");
            config.excludedServers = List.of("aero-auth");
            config.serverLabels = Map.of(
                    "aero", "Аеронавтика",
                    "aero-lobby", "Лобі"
            );
        });
        DiscordServerPolicy policy = new DiscordServerPolicy(settings);

        assertThat(policy.includes("AERO")).isTrue();
        assertThat(policy.includes("aero-auth")).isFalse();
        assertThat(policy.includes("survival")).isFalse();
        assertThat(policy.label("aero")).isEqualTo("Аеронавтика");
        assertThat(policy.monitorTargets(List.of("aero-auth", "aero-lobby", "aero")))
                .containsExactly("aero", "aero-lobby");
    }

    @Test
    void emptyIncludeSelectsRegisteredServersExceptAeroAuth() {
        DiscordServerPolicy policy = new DiscordServerPolicy(DiscordTestSettings.create(tempDir));

        assertThat(policy.monitorTargets(List.of("zeta", "aero-auth", "alpha")))
                .containsExactly("alpha", "zeta");
    }
}
