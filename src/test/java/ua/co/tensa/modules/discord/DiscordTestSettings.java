package ua.co.tensa.modules.discord;

import ua.co.tensa.Tensa;
import ua.co.tensa.modules.discord.data.DiscordConfig;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

final class DiscordTestSettings {
    private DiscordTestSettings() {
    }

    static DiscordSettings create(Path pluginDirectory) {
        return create(pluginDirectory, ignored -> { });
    }

    static DiscordSettings create(Path pluginDirectory, Consumer<DiscordConfig> customize) {
        Tensa.pluginPath = pluginDirectory;
        DiscordConfig config = new DiscordConfig();
        config.guildId = "12345678901234567";
        config.channelId = "22345678901234567";
        config.linkedRoleId = "32345678901234567";
        config.botToken = "test-token";
        customize.accept(config);
        return config.settings(Map.of());
    }
}
