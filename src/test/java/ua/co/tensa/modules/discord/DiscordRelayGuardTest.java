package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.co.tensa.modules.discord.data.DiscordConfig;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscordRelayGuardTest {
    @TempDir
    Path tempDir;

    @Test
    void onlyProtectsListedChannelsInBothDirections() {
        DiscordSettings settings = settings("only", List.of("global"), List.of(
                "minecraft_to_discord", "discord_to_minecraft"));

        assertThat(settings.relayGuard().protects(
                DiscordRelayGuard.Direction.MINECRAFT_TO_DISCORD, "global")).isTrue();
        assertThat(settings.relayGuard().protects(
                DiscordRelayGuard.Direction.DISCORD_TO_MINECRAFT, "GLOBAL")).isTrue();
        assertThat(settings.relayGuard().protects(
                DiscordRelayGuard.Direction.MINECRAFT_TO_DISCORD, "staff")).isFalse();
    }

    @Test
    void exceptProtectsEveryChannelOutsideTheList() {
        DiscordSettings settings = settings("except", List.of("staff"), List.of("minecraft_to_discord"));

        assertThat(settings.relayGuard().protects(
                DiscordRelayGuard.Direction.MINECRAFT_TO_DISCORD, "global")).isTrue();
        assertThat(settings.relayGuard().protects(
                DiscordRelayGuard.Direction.MINECRAFT_TO_DISCORD, "staff")).isFalse();
        assertThat(settings.relayGuard().protects(
                DiscordRelayGuard.Direction.DISCORD_TO_MINECRAFT, "global")).isFalse();
    }

    @Test
    void rejectsUnknownDirectionsBeforeActivation() {
        assertThatThrownBy(() -> settings("only", List.of("global"), List.of("sideways")))
                .isInstanceOf(DiscordConfigurationException.class)
                .hasMessageContaining("directions");
    }

    private DiscordSettings settings(String mode, List<String> channels, List<String> directions) {
        return DiscordTestSettings.create(tempDir, config -> configureGuard(config, mode, channels, directions));
    }

    private static void configureGuard(
            DiscordConfig config,
            String mode,
            List<String> channels,
            List<String> directions
    ) {
        LinkedHashMap<String, Object> guard = new LinkedHashMap<>();
        guard.put("enabled", true);
        guard.put("mode", mode);
        guard.put("channels", channels);
        guard.put("directions", directions);
        config.proxyChat.put("require_link_to_relay", guard);
    }
}
