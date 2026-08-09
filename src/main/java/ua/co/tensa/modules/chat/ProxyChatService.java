package ua.co.tensa.modules.chat;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import ua.co.tensa.config.model.YamlAdapter;

import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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

    private record PlayerState(long sentAt, long duplicateAt, String duplicateKey) {
    }

    private record Settings(
            boolean enabled,
            boolean nativeGlobalChat,
            Set<String> excludedServers,
            Map<String, String> serverAliases,
            int maxLength,
            long cooldownMillis,
            long duplicateWindowMillis,
            int maxRepeatedCharacters,
            String discordFormat,
            String cooldownMessage,
            String duplicateMessage,
            String linkRequiredMessage
    ) {
    }

    private final Clock clock;
    private final YamlAdapter chatConfig;
    private final YamlAdapter discordConfig;
    private final ProxyChatRelay outbound;
    private final ConcurrentHashMap<UUID, PlayerState> playerStates = new ConcurrentHashMap<>();
    private final LongAdder clickableUrlsRendered = new LongAdder();
    private volatile Settings settings;

    public ProxyChatService(
            YamlAdapter chatConfig,
            YamlAdapter discordConfig,
            ProxyChatRelay outbound
    ) {
        this(Clock.systemUTC(), chatConfig, discordConfig, outbound);
    }

    ProxyChatService(
            Clock clock,
            YamlAdapter chatConfig,
            YamlAdapter discordConfig,
            ProxyChatRelay outbound
    ) {
        this.clock = clock;
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

        settings = new Settings(
                config.getBoolean("proxy_chat.enabled", true),
                nativeGlobalChatEnabled(
                        config.getBoolean("proxy_chat.enabled", true),
                        chatConfig.getBoolean("global.enabled", true),
                        chatConfig.getBoolean("global.native", true)
                ),
                Set.copyOf(excludedServers),
                Map.copyOf(serverAliases),
                Math.max(1, config.getInt("proxy_chat.max_length", 256)),
                Math.max(0L, config.getLong("proxy_chat.cooldown_millis", 1500L)),
                Math.max(0L, config.getLong("proxy_chat.duplicate_window_millis", 15000L)),
                Math.max(1, config.getInt("proxy_chat.max_repeated_characters", 4)),
                config.getString("proxy_chat.discord_format", "<color:#5865f2>[Discord]</color> <white>{player}: {message}</white>"),
                config.getString("proxy_chat.cooldown_message", "<yellow>Зачекайте перед наступним повідомленням.</yellow>"),
                config.getString("proxy_chat.duplicate_message", "<yellow>Не повторюйте повідомлення.</yellow>"),
                config.getString(
                        "proxy_chat.require_link_to_relay.minecraft_message",
                        "<yellow>Прив'яжіть Discord через /discord link.</yellow>"
                )
        );
        playerStates.clear();
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
        String message = ProxyChatText.sanitize(
                rawMessage,
                current.maxLength(),
                current.maxRepeatedCharacters()
        );
        if (message.isEmpty()) {
            return false;
        }

        long now = clock.millis();
        String duplicateKey = ProxyChatText.duplicateKey(message);
        PlayerState previous = playerStates.get(player.getUniqueId());
        if (previous != null && now - previous.sentAt() < current.cooldownMillis()) {
            Message.privateMessage(player, current.cooldownMessage());
            return false;
        }
        if (previous != null
                && duplicateKey.equals(previous.duplicateKey())
                && now - previous.duplicateAt() < current.duplicateWindowMillis()) {
            Message.privateMessage(player, current.duplicateMessage());
            return false;
        }

        playerStates.put(player.getUniqueId(), new PlayerState(now, now, duplicateKey));
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
                ProxyChatRelay.Result relayResult = outbound.publish(payload);
                if (relayResult == ProxyChatRelay.Result.LINK_REQUIRED) {
                    Message.privateMessage(player, current.linkRequiredMessage());
                }
            } catch (RuntimeException exception) {
                Message.warn("Chat relay rejected a message: " + exception.getClass().getSimpleName());
            }
        }
        return true;
    }

    public void publishExternal(String source, String author, String rawMessage) {
        Settings current = settings;
        String message = ProxyChatText.sanitize(
                rawMessage,
                current.maxLength(),
                current.maxRepeatedCharacters()
        );
        String player = ProxyChatText.sanitize(author, 64, 8);
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
        playerStates.clear();
    }

    public void forget(UUID playerId) {
        if (playerId != null) {
            playerStates.remove(playerId);
        }
    }

    public int stateSize() {
        return playerStates.size();
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
                booleanValue(section.get("relay_to_discord"), false),
                stringValue(section.get("format"), "<white>{player}: {message}</white>")
        );
    }

    private void broadcast(ProxyChatMessage payload, ChannelDefinition definition, Settings current) {
        Map<String, String> values = new HashMap<>();
        values.put("server", payload.server());
        values.put("player", payload.playerName());
        ChatMessageRenderer.Result rendered = ChatMessageRenderer.render(
                definition.format(), values, payload.message());
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
        return value == null ? fallback : String.valueOf(value);
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
