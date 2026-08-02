package ua.co.tensa.modules.discord;

import java.util.Map;

final class DiscordEventFormatter {
    private final DiscordSettings settings;
    private final DiscordServerPolicy servers;

    DiscordEventFormatter(DiscordSettings settings, DiscordServerPolicy servers) {
        this.settings = settings;
        this.servers = servers;
    }

    String join(String playerName, String serverName) {
        return render(settings.joinFormat(), Map.of(
                "player", value(playerName, 80),
                "server", server(serverName)
        ));
    }

    String quit(String playerName, String serverName) {
        return render(settings.quitFormat(), Map.of(
                "player", value(playerName, 80),
                "server", server(serverName)
        ));
    }

    String switchServer(String playerName, String fromServer, String toServer) {
        return render(settings.serverSwitchFormat(), Map.of(
                "player", value(playerName, 80),
                "from", server(fromServer),
                "to", server(toServer)
        ));
    }

    String backendUnavailable(String serverName) {
        return render(settings.backendUnavailableFormat(), Map.of("server", server(serverName)));
    }

    String backendRecovered(String serverName) {
        return render(settings.backendRecoveredFormat(), Map.of("server", server(serverName)));
    }

    String advancement(String playerName, String serverName, String advancement) {
        return render(settings.advancementFormat(), Map.of(
                "player", value(playerName, 80),
                "server", server(serverName),
                "advancement", value(advancement, 200)
        ));
    }

    private String server(String serverName) {
        return value(servers.label(serverName), 80);
    }

    private String render(String template, Map<String, String> values) {
        return DiscordSanitizer.truncate(
                DiscordMessages.render(template, values),
                settings.maxDiscordMessageLength()
        );
    }

    private static String value(String value, int maxCodePoints) {
        return DiscordSanitizer.forDiscord(value, maxCodePoints);
    }
}
