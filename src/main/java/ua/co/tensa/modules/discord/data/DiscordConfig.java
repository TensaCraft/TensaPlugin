package ua.co.tensa.modules.discord.data;

import org.spongepowered.configurate.CommentedConfigurationNode;
import ua.co.tensa.config.model.ConfigBase;
import ua.co.tensa.config.model.ann.CfgKey;
import ua.co.tensa.modules.discord.DiscordSettings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DiscordConfig extends ConfigBase {
    @CfgKey(value = "enabled", comment = "Enable the Discord bot, linking, relay and announcements inside Communications")
    public boolean enabled = false;

    @CfgKey(value = "proxy_chat", comment = "Proxy-wide player chat transport and Discord-to-Minecraft display settings")
    public Map<String, Object> proxyChat = defaults(
            entry("enabled", true),
            entry("excluded_servers", List.of("auth", "aero-auth")),
            entry("server_aliases", defaults(
                    entry("aeronautics", "Aeronautics"),
                    entry("creative", "Creative")
            )),
            entry("max_length", 256),
            entry("cooldown_millis", 1500),
            entry("duplicate_window_millis", 15000),
            entry("max_repeated_characters", 4),
            entry("format", "<color:#f4c15d>{player}</color> <dark_gray>»</dark_gray> <white>{message}</white>"),
            entry("discord_format", "<dark_gray>[</dark_gray><color:#5865f2>Discord</color><dark_gray>]</dark_gray> <color:#7fd7ff>{player}</color> <dark_gray>></dark_gray> <white>{message}</white>"),
            entry("cooldown_message", "<color:#ffb84d>Зачекайте трохи перед наступним повідомленням.</color>"),
            entry("duplicate_message", "<color:#ffb84d>Не надсилайте однакові повідомлення поспіль.</color>")
    );

    @CfgKey(value = "bot.token", comment = "Secret. Prefer TENSA_DISCORD_BOT_TOKEN; this key is never generated automatically")
    public String botToken = "";

    @CfgKey(value = "webhook.url", comment = "Secret. Prefer TENSA_DISCORD_WEBHOOK_URL; this key is never generated automatically")
    public String webhookUrl = "";

    @CfgKey(value = "announcements.route.webhook_url", comment = "Secret. Prefer TENSA_DISCORD_EVENTS_WEBHOOK_URL; this key is never generated automatically")
    public String eventsWebhookUrl = "";

    @CfgKey(value = "guild_id", comment = "Single Discord guild used by relay and account linking")
    public String guildId = "";

    @CfgKey(value = "channel_id", comment = "Single Discord text channel used by the global chat relay")
    public String channelId = "";

    @CfgKey(value = "announcements.route.channel_id", comment = "Optional Discord channel for events; blank uses channel_id")
    public String eventsChannelId = "";

    @CfgKey(value = "linked_role_id", comment = "Optional role granted to linked Discord accounts")
    public String linkedRoleId = "";

    @CfgKey(value = "link_command_name", comment = "Guild-scoped Discord slash command name")
    public String linkCommandName = "link";

    @CfgKey(value = "relay.minecraft_to_discord", comment = "Relay successful global proxy-chat messages to Discord")
    public boolean minecraftToDiscord = true;

    @CfgKey(value = "relay.discord_to_minecraft", comment = "Relay messages from the configured Discord channel to global proxy chat")
    public boolean discordToMinecraft = true;

    @CfgKey(value = "relay.minecraft_to_discord_format", comment = "Plain Discord message template")
    public String minecraftToDiscordFormat = "{message}";

    @CfgKey(value = "relay.avatar_url_template", comment = "HTTPS avatar URL supporting {uuid} and {player}")
    public String avatarUrlTemplate = "https://mc-heads.net/avatar/{player}/128";

    @CfgKey(value = "announcements.join", comment = "Send player join messages to Discord")
    public boolean joinMessages = false;

    @CfgKey(value = "announcements.quit", comment = "Send player quit messages to Discord")
    public boolean quitMessages = false;

    @CfgKey(value = "announcements.server_switch", comment = "Send proxy server-switch messages to Discord")
    public boolean serverSwitchMessages = false;

    @CfgKey(value = "announcements.backend_status.enabled", comment = "Send backend unavailability and recovery messages to Discord")
    public boolean backendStatusMessages = false;

    @CfgKey(value = "announcements.advancements", comment = "Accept advancement events from the configured backend bridge")
    public boolean advancementMessages = false;

    @CfgKey(value = "announcements.servers.include", comment = "Backend names eligible for events; empty selects every registered backend")
    public List<String> includedServers = new ArrayList<>();

    @CfgKey(value = "announcements.servers.exclude", comment = "Backend names never shown in Discord events")
    public List<String> excludedServers = new ArrayList<>(List.of("aero-auth"));

    @CfgKey(value = "announcements.servers.labels", comment = "Player-facing labels keyed by Velocity backend name")
    public Map<String, Object> serverLabels = new LinkedHashMap<>();

    @CfgKey(value = "announcements.join_format", comment = "Plain Discord join template")
    public String joinFormat = "🟢 {player} приєднався до «{server}».";

    @CfgKey(value = "announcements.quit_format", comment = "Plain Discord quit template")
    public String quitFormat = "⚪ {player} вийшов із «{server}».";

    @CfgKey(value = "announcements.server_switch_format", comment = "Plain Discord server-switch template")
    public String serverSwitchFormat = "🔄 {player}: «{from}» → «{to}».";

    @CfgKey(value = "announcements.backend_unavailable_format", comment = "Plain Discord backend-unavailable template")
    public String backendUnavailableFormat = "🔴 «{server}» тимчасово недоступний.";

    @CfgKey(value = "announcements.backend_recovered_format", comment = "Plain Discord backend-recovery template")
    public String backendRecoveredFormat = "🟢 «{server}» знову доступний.";

    @CfgKey(value = "announcements.advancement_format", comment = "Plain Discord advancement template")
    public String advancementFormat = "🏆 {player} отримав досягнення «{advancement}» на «{server}».";

    @CfgKey(value = "announcements.backend_status.poll_interval_seconds", comment = "Interval between asynchronous backend health probes")
    public int backendStatusPollIntervalSeconds = 15;

    @CfgKey(value = "announcements.backend_status.ping_timeout_seconds", comment = "Timeout for one backend health probe")
    public int backendStatusPingTimeoutSeconds = 5;

    @CfgKey(value = "announcements.backend_status.debounce_seconds", comment = "Minimum time a changed backend state must remain stable")
    public int backendStatusDebounceSeconds = 20;

    @CfgKey(value = "announcements.backend_status.confirmations", comment = "Consecutive probes required before publishing a state transition")
    public int backendStatusConfirmations = 2;

    @CfgKey(value = "announcements.backend_status.max_monitored_servers", comment = "Bound on monitored backend state")
    public int maxMonitoredServers = 128;

    @CfgKey(value = "announcements.backend_bridge.channel", comment = "Velocity plugin-message channel used by TensaProxy advancement events")
    public String backendEventChannel = "tensa:discord_events";

    @CfgKey(value = "announcements.advancement_dedup_seconds", comment = "Window suppressing duplicate backend advancement packets")
    public int advancementDedupSeconds = 10;

    @CfgKey(value = "limits.max_minecraft_message_length", comment = "Maximum Discord message length relayed into Minecraft")
    public int maxMinecraftMessageLength = 256;

    @CfgKey(value = "limits.max_discord_message_length", comment = "Maximum generated Discord message length")
    public int maxDiscordMessageLength = 1_000;

    @CfgKey(value = "limits.queue_capacity", comment = "Bound for both relay directions; new messages are rejected when full")
    public int queueCapacity = 256;

    @CfgKey(value = "limits.event_rate_per_minute", comment = "Maximum join, quit, switch, status and advancement messages per minute")
    public int eventRatePerMinute = 60;

    @CfgKey(value = "limits.event_state_capacity", comment = "Bound for player presence and event deduplication state")
    public int eventStateCapacity = 4_096;

    @CfgKey(value = "delivery.attempts", comment = "Bounded delivery attempts before a message is dropped")
    public int deliveryAttempts = 3;

    @CfgKey(value = "delivery.timeout_seconds", comment = "Timeout for one Discord REST delivery or role operation")
    public int deliveryTimeoutSeconds = 10;

    @CfgKey(value = "gateway.max_reconnect_delay_seconds", comment = "Maximum JDA gateway reconnect delay")
    public int reconnectMaxDelaySeconds = 120;

    @CfgKey(value = "linking.store_file", comment = "Atomic local JSON store, relative to the plugin directory")
    public String linkStoreFile = "discord/links.json";

    @CfgKey(value = "linking.code_ttl_seconds", comment = "Lifetime of a one-time link code")
    public int linkCodeTtlSeconds = 600;

    @CfgKey(value = "linking.code_length", comment = "Length of generated one-time link codes")
    public int linkCodeLength = 8;

    @CfgKey(value = "linking.executor_queue_capacity", comment = "Bound for account-linking disk and Discord role operations")
    public int linkExecutorCapacity = 32;

    public DiscordConfig() {
        super("discord.yml");
    }

    public DiscordSettings settings(Map<String, String> environment) {
        return DiscordSettings.from(this, environment);
    }

    @Override
    public synchronized void reloadCfg() {
        super.reloadCfg();
        boolean removedGeneratedSecret = false;
        if ((botToken == null || botToken.isBlank()) && contains("bot.token")) {
            setNodeValue(node("bot.token"), null);
            removedGeneratedSecret = true;
        }
        if ((webhookUrl == null || webhookUrl.isBlank()) && contains("webhook.url")) {
            setNodeValue(node("webhook.url"), null);
            removedGeneratedSecret = true;
        }
        if ((eventsWebhookUrl == null || eventsWebhookUrl.isBlank()) && contains("announcements.route.webhook_url")) {
            setNodeValue(node("announcements.route.webhook_url"), null);
            removedGeneratedSecret = true;
        }
        if (removedGeneratedSecret) {
            if (node("bot").childrenMap().isEmpty()) {
                setNodeValue(node("bot"), null);
            }
            if (node("webhook").childrenMap().isEmpty()) {
                setNodeValue(node("webhook"), null);
            }
            if (node("announcements.route").childrenMap().isEmpty()) {
                setNodeValue(node("announcements.route"), null);
            }
            save();
        }
    }

    @Override
    protected boolean shouldWriteDefault(String basePath, Object defaultValue, CommentedConfigurationNode yaml) {
        return !"bot.token".equals(basePath)
                && !"webhook.url".equals(basePath)
                && !"announcements.route.webhook_url".equals(basePath);
    }

    private static Map<String, Object> defaults(Object... values) {
        Map<String, Object> defaults = new LinkedHashMap<>();
        for (Object value : values) {
            if (value instanceof Object[] entry && entry.length == 2) {
                defaults.put(String.valueOf(entry[0]), entry[1]);
            }
        }
        return defaults;
    }

    private static Object[] entry(String key, Object value) {
        return new Object[]{key, value};
    }
}
