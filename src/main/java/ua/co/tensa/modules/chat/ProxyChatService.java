package ua.co.tensa.modules.chat;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import ua.co.tensa.config.model.YamlAdapter;
import ua.co.tensa.text.TextPipeline;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;

public final class ProxyChatService {
    public record ChannelDefinition(
            String key,
            boolean enabled,
            String permission,
            boolean seeAll,
            boolean relayToDiscord,
            String format
    ) {
    }

    private record Settings(
            boolean enabled,
            boolean nativeGlobalChat,
            Set<String> excludedServers,
            Map<String, String> serverAliases,
            boolean relayToDiscord,
            Set<String> relayChannels,
            int maxLength,
            String discordFormat
    ) {
    }

    private final YamlAdapter chatConfig;
    private final YamlAdapter discordConfig;
    private final ProxyChatRelay outbound;
    private final LongAdder clickableUrlsRendered = new LongAdder();
    private volatile Settings settings;

    public ProxyChatService(
            YamlAdapter chatConfig,
            YamlAdapter discordConfig,
            ProxyChatRelay outbound
    ) {
        this.chatConfig = java.util.Objects.requireNonNull(chatConfig, "chatConfig");
        this.discordConfig = java.util.Objects.requireNonNull(discordConfig, "discordConfig");
        this.outbound = outbound == null ? ignored -> ProxyChatRelay.Result.DISABLED : outbound;
        reload();
    }

    public void reload() {
        YamlAdapter config = discordConfig;
        Set<String> excludedServers = new HashSet<>();
        for (String server : config.getStringList("proxy_chat.excluded_servers")) {
            if (server != null && !server.isBlank()) {
                excludedServers.add(server.trim().toLowerCase(java.util.Locale.ROOT));
            }
        }
        Map<String, String> serverAliases = new HashMap<>();
        Map<String, Object> configuredAliases = config.getSection("proxy_chat.server_aliases");
        if (configuredAliases == null) {
            configuredAliases = Map.of();
        }
        for (Map.Entry<String, Object> entry : configuredAliases.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String key = entry.getKey().trim().toLowerCase(java.util.Locale.ROOT);
            String value = String.valueOf(entry.getValue()).trim();
            if (!key.isBlank() && !value.isBlank()) {
                serverAliases.put(key, value);
            }
        }
        Set<String> relayChannels = new HashSet<>();
        for (String channel : config.getStringList("relay.minecraft_to_discord.channels")) {
            if (channel != null && !channel.isBlank()) {
                relayChannels.add(channel.trim().toLowerCase(java.util.Locale.ROOT));
            }
        }

        settings = new Settings(
                config.getBoolean("proxy_chat.enabled", true),
                nativeGlobalChatEnabled(
                        config.getBoolean("proxy_chat.enabled", true),
                        chatConfig.getBoolean("global.enabled", true),
                        chatConfig.getBoolean("global.native", true)
                ),
                Set.copyOf(excludedServers),
                Map.copyOf(serverAliases),
                config.getBoolean("relay.minecraft_to_discord.enabled", true),
                Set.copyOf(relayChannels),
                256,
                config.getString("proxy_chat.discord_format", "<color:#5865f2>[Discord]</color> <white>{player}: {message}</white>")
        );
    }

    public boolean shouldIntercept(Player player) {
        Settings current = settings;
        if (!current.enabled()) {
            return false;
        }

        String server = serverName(player);
        return !current.excludedServers().contains(server.toLowerCase(java.util.Locale.ROOT));
    }

    public boolean shouldInterceptNative(Player player) {
        Settings current = settings;
        return current.nativeGlobalChat() && shouldIntercept(player);
    }

    static boolean nativeGlobalChatEnabled(
            boolean proxyEnabled,
            boolean globalEnabled,
            boolean nativeEnabled
    ) {
        return proxyEnabled && globalEnabled && nativeEnabled;
    }

    public boolean publishPlayer(Player player, String rawMessage, String channel) {
        if (player == null || !shouldIntercept(player)) {
            return false;
        }

        Settings current = settings;
        ChannelDefinition definition = resolveChannel(channel);
        if (!definition.enabled()) {
            return false;
        }
        String message = ProxyChatText.sanitize(rawMessage, current.maxLength());
        if (message.isEmpty()) {
            return false;
        }

        String server = displayServerName(serverName(player), current);
        ProxyChatMessage payload = new ProxyChatMessage(
                ProxyChatMessage.Origin.MINECRAFT,
                channel == null || channel.isBlank() ? "global" : channel,
                server,
                player.getUniqueId(),
                player.getUsername(),
                message
        );
        broadcast(payload, definition, current);
        if (definition.relayToDiscord()) {
            try {
                outbound.publish(payload);
            } catch (RuntimeException exception) {
                Message.warn("Chat relay rejected a message: " + exception.getClass().getSimpleName());
            }
        }
        return true;
    }

    public void publishExternal(String source, String author, String rawMessage) {
        Settings current = settings;
        String message = ProxyChatText.sanitize(rawMessage, current.maxLength());
        String player = ProxyChatText.sanitize(author, 64);
        if (message.isEmpty() || player.isEmpty()) {
            return;
        }

        ProxyChatMessage payload = new ProxyChatMessage(
                ProxyChatMessage.Origin.DISCORD,
                "global",
                source == null ? "Discord" : source,
                null,
                player,
                message
        );
        broadcast(payload, new ChannelDefinition(
                "global", true, "", true, false, current.discordFormat()), current);
    }

    public void clear() {
    }

    public void forget(java.util.UUID playerId) {
    }

    public int stateSize() {
        return 0;
    }

    public void recordClickableUrls(int count) {
        if (count > 0) {
            clickableUrlsRendered.add(count);
        }
    }

    public long clickableUrlsRendered() {
        return clickableUrlsRendered.sum();
    }

    public ChannelDefinition resolveChannel(String channel) {
        String key = channel == null || channel.isBlank()
                ? "global"
                : channel.trim().toLowerCase(java.util.Locale.ROOT);
        Map<String, Object> section = chatConfig.getSection(key);
        if (section == null) {
            section = Map.of();
        }
        return new ChannelDefinition(
                key,
                booleanValue(section.get("enabled"), true),
                stringValue(section.get("permission"), ""),
                booleanValue(section.get("see_all"), false),
                settings.relayToDiscord() && settings.relayChannels().contains(key),
                stringValue(section.get("format"), "<white>{player}: {message}</white>")
        );
    }

    private void broadcast(ProxyChatMessage payload, ChannelDefinition definition, Settings current) {
        Map<String, String> values = new HashMap<>();
        values.put("server", payload.server());
        values.put("player", payload.playerName());
        Player placeholderContext = payload.playerUuid() == null
                ? null
                : Tensa.server.getPlayer(payload.playerUuid()).orElse(null);
        TextPipeline.Rendered rendered = TextPipeline.chat(
                placeholderContext, definition.format(), values, payload.message());
        recordClickableUrls(rendered.clickableUrls());

        for (Player player : Tensa.server.getAllPlayers()) {
            String playerServer = serverName(player).toLowerCase(java.util.Locale.ROOT);
            if (current.excludedServers().contains(playerServer)) {
                continue;
            }
            if (!definition.seeAll()
                    && !definition.permission().isBlank()
                    && !player.hasPermission(definition.permission())) {
                continue;
            }
            Message.send(player, rendered.component());
        }
        Message.send(Tensa.server.getConsoleCommandSource(), rendered.component());
    }

    private static String stringValue(Object value, String fallback) {
        return value instanceof String text ? text : fallback;
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value instanceof String text ? Boolean.parseBoolean(text) : fallback;
    }

    private static String serverName(CommandSource source) {
        if (!(source instanceof Player player)) {
            return "Proxy";
        }
        return player.getCurrentServer()
                .map(connection -> connection.getServerInfo().getName())
                .orElse("Proxy");
    }

    private static String displayServerName(String server, Settings settings) {
        if (server == null || server.isBlank()) {
            return "Proxy";
        }
        return settings.serverAliases().getOrDefault(
                server.toLowerCase(java.util.Locale.ROOT),
                server
        );
    }
}
