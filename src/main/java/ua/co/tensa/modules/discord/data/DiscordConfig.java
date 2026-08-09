package ua.co.tensa.modules.discord.data;

import org.spongepowered.configurate.CommentedConfigurationNode;
import ua.co.tensa.config.model.ConfigBase;
import ua.co.tensa.config.model.ann.CfgKey;
import ua.co.tensa.modules.discord.DiscordSettings;
import ua.co.tensa.modules.discord.CommunicationsConfigBootstrap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DiscordConfig extends ConfigBase {
    private static final Set<String> INTERNAL_DEFAULTS = Set.of(
            "announcements.backend_status.poll_interval_seconds",
            "announcements.backend_status.ping_timeout_seconds",
            "announcements.backend_status.debounce_seconds",
            "announcements.backend_status.confirmations",
            "announcements.backend_status.max_monitored_servers",
            "achievements.dedup_seconds",
            "limits.max_minecraft_message_length",
            "limits.max_discord_message_length",
            "limits.queue_capacity",
            "limits.event_rate_per_minute",
            "limits.event_state_capacity",
            "limits.max_links",
            "delivery.attempts",
            "delivery.timeout_seconds",
            "gateway.max_reconnect_delay_seconds",
            "linking.executor_queue_capacity",
            "linking.post_link_queue_capacity",
            "linking.nickname_sync.attempts",
            "linking.nickname_sync.retry_base_delay_millis",
            "diagnostics.transition_logs",
            "scheduler.max_jobs",
            "scheduler.worker_threads",
            "scheduler.queue_capacity",
            "scheduler.timeout_seconds",
            "scheduler.max_attempts",
            "scheduler.backoff_millis",
            "scheduler.max_backoff_seconds",
            "scheduler.jitter_percent"
    );
    @CfgKey(value = "config_version", comment = "Communications Discord configuration schema version")
    public int configVersion = CommunicationsConfigBootstrap.CONFIG_VERSION;

    @CfgKey(value = "bot.enabled", comment = "Enable the Discord bot, linking, relay and announcements inside Communications")
    public boolean enabled = false;

    @CfgKey(value = "webhook.enabled", comment = "Prefer configured webhooks for chat and announcement delivery")
    public boolean webhookEnabled = true;

    @CfgKey(value = "proxy_chat.webhook.auto_create", comment = "Create or recover a managed chat webhook when no valid configured webhook is available")
    public boolean webhookAutoCreate = false;

    @CfgKey(value = "proxy_chat.webhook.name", comment = "Name of the bot-owned webhook managed in the configured relay channel")
    public String webhookName = "Tensa Communications";

    @CfgKey(value = "scheduler.max_jobs", comment = "Advanced: maximum lifecycle-owned scheduled jobs")
    public int schedulerMaxJobs = 512;

    @CfgKey(value = "scheduler.worker_threads", comment = "Advanced: bounded scheduler worker count")
    public int schedulerWorkerThreads = 2;

    @CfgKey(value = "scheduler.queue_capacity", comment = "Advanced: bounded scheduler worker queue")
    public int schedulerQueueCapacity = 256;

    @CfgKey(value = "scheduler.timeout_seconds", comment = "Advanced: default job timeout")
    public int schedulerTimeoutSeconds = 30;

    @CfgKey(value = "scheduler.max_attempts", comment = "Advanced: default maximum job attempts")
    public int schedulerMaxAttempts = 1;

    @CfgKey(value = "scheduler.backoff_millis", comment = "Advanced: default retry base delay")
    public int schedulerBackoffMillis = 1_000;

    @CfgKey(value = "scheduler.max_backoff_seconds", comment = "Advanced: default retry delay cap")
    public int schedulerMaxBackoffSeconds = 30;

    @CfgKey(value = "scheduler.jitter_percent", comment = "Advanced: default retry jitter percent")
    public int schedulerJitterPercent = 20;

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
            entry("duplicate_message", "<color:#ffb84d>Не надсилайте однакові повідомлення поспіль.</color>"),
            entry("require_link_to_relay", defaults(
                    entry("enabled", false),
                    entry("directions", List.of("minecraft_to_discord", "discord_to_minecraft")),
                    entry("mode", "only"),
                    entry("channels", List.of("global")),
                    entry("minecraft_message", "<yellow>Прив'яжіть Discord через <white>/discord link</white>, щоб надіслати це повідомлення у Discord.</yellow>"),
                    entry("discord_reply", "Прив'яжіть Minecraft-акаунт через /discord link у грі, щоб писати в ігровий чат."),
                    entry("reply_delete_after_seconds", 10),
                    entry("feedback_cooldown_seconds", 30)
            ))
    );

    @CfgKey(value = "embeds", comment = "Validated Discord embed templates; blank image fields are omitted")
    public Map<String, Object> embeds = defaults(
            entry("join", embed(false, "Гравець приєднався", "🟢 {player} приєднався до «{server}».", "#57F287")),
            entry("quit", embed(false, "Гравець вийшов", "⚪ {player} вийшов із «{server}».", "#95A5A6")),
            entry("server_switch", embed(false, "Перехід між серверами", "🔄 {player}: «{from}» → «{to}».", "#5865F2")),
            entry("advancement", embed(false, "Нове досягнення", "🏆 {player} отримав досягнення «{advancement}» на «{server}».", "#FEE75C")),
            entry("link_success", embed(true, "Прив'язку завершено", "Акаунт успішно прив'язано до {player}.", "#57F287")),
            entry("link_error", embed(true, "Не вдалося прив'язати акаунт", "{message}", "#ED4245")),
            entry("backend_unavailable", embed(false, "Сервер недоступний", "🔴 «{server}» тимчасово недоступний.", "#ED4245")),
            entry("backend_recovered", embed(false, "Сервер знову доступний", "🟢 «{server}» знову доступний.", "#57F287"))
    );

    @CfgKey(value = "bot.token", comment = "Secret. Prefer TENSA_DISCORD_BOT_TOKEN; this key is never generated automatically")
    public String botToken = "";

    @CfgKey(value = "webhook.chat_url", comment = "Secret. Prefer TENSA_DISCORD_WEBHOOK_URL; this key is never generated automatically")
    public String webhookUrl = "";

    @CfgKey(value = "webhook.announcements_url", comment = "Secret. Prefer TENSA_DISCORD_EVENTS_WEBHOOK_URL; this key is never generated automatically")
    public String eventsWebhookUrl = "";

    @CfgKey(value = "discord_ids.guild_id", comment = "Single Discord guild used by relay and account linking")
    public String guildId = "";

    @CfgKey(value = "discord_ids.main_channel_id", comment = "Single Discord text channel used by the global chat relay")
    public String channelId = "";

    @CfgKey(value = "discord_ids.announcements_channel_id", comment = "Optional Discord channel for events; blank uses main_channel_id")
    public String eventsChannelId = "";

    @CfgKey(value = "discord_ids.linked_role_id", comment = "Optional role granted to linked Discord accounts")
    public String linkedRoleId = "";

    @CfgKey(value = "linking.command_name", comment = "Guild-scoped Discord slash command name")
    public String linkCommandName = "link";

    @CfgKey(value = "relay.minecraft_to_discord.enabled", comment = "Relay successful global proxy-chat messages to Discord")
    public boolean minecraftToDiscord = true;

    @CfgKey(value = "relay.discord_to_minecraft.enabled", comment = "Relay messages from the configured Discord channel to global proxy chat")
    public boolean discordToMinecraft = true;

    @CfgKey(value = "relay.minecraft_to_discord.format", comment = "Plain Discord message template")
    public String minecraftToDiscordFormat = "{message}";

    @CfgKey(value = "relay.minecraft_to_discord.avatar_url_template", comment = "HTTPS avatar URL supporting {uuid} and {player}")
    public String avatarUrlTemplate = "https://mc-heads.net/avatar/{player}/128";

    @CfgKey(value = "achievements.enabled", comment = "Accept advancement events from the configured backend bridge")
    public boolean advancementMessages = false;

    @CfgKey(value = "announcements.servers.include", comment = "Backend names eligible for events; empty selects every registered backend")
    public List<String> includedServers = new ArrayList<>();

    @CfgKey(value = "announcements.servers.exclude", comment = "Backend names never shown in Discord events")
    public List<String> excludedServers = new ArrayList<>(List.of("aero-auth"));

    @CfgKey(value = "announcements.servers.labels", comment = "Player-facing labels keyed by Velocity backend name")
    public Map<String, Object> serverLabels = new LinkedHashMap<>();

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

    @CfgKey(value = "achievements.backend_bridge.channel", comment = "Velocity plugin-message channel used by TensaProxy advancement events")
    public String backendEventChannel = "tensa:discord_events";

    @CfgKey(value = "achievements.dedup_seconds", comment = "Window suppressing duplicate backend advancement packets")
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

    @CfgKey(value = "linking.code_ttl_seconds", comment = "Lifetime of a one-time link code")
    public int linkCodeTtlSeconds = 600;

    @CfgKey(value = "linking.code_length", comment = "Length of generated one-time link codes")
    public int linkCodeLength = 8;

    @CfgKey(value = "linking.executor_queue_capacity", comment = "Bound for account-linking storage and Discord role operations")
    public int linkExecutorCapacity = 32;

    @CfgKey(value = "linking.post_link_queue_capacity", comment = "Bound for announcement and nickname work after a durable link")
    public int postLinkQueueCapacity = 32;

    @CfgKey(value = "linking.link_announcement.enabled", comment = "Announce a newly linked Minecraft account to eligible online players")
    public boolean linkAnnouncementEnabled = false;

    @CfgKey(value = "linking.link_announcement.format", comment = "Single MiniMessage template for a successful account link")
    public String linkAnnouncementFormat = "<green>{player}</green> <gray>прив'язав Discord-акаунт.</gray>";

    @CfgKey(value = "linking.link_announcement.servers.include", comment = "Recipient backends for link announcements; empty includes all")
    public List<String> linkAnnouncementIncludedServers = new ArrayList<>();

    @CfgKey(value = "linking.link_announcement.servers.exclude", comment = "Recipient backends excluded from link announcements")
    public List<String> linkAnnouncementExcludedServers = new ArrayList<>(List.of("auth", "aero-auth"));

    @CfgKey(value = "linking.nickname_sync.mode", comment = "Discord nickname synchronization: disabled or minecraft")
    public String nicknameSyncMode = "disabled";

    @CfgKey(value = "linking.nickname_sync.attempts", comment = "Attempts for transient Discord nickname failures")
    public int nicknameSyncAttempts = 3;

    @CfgKey(value = "linking.nickname_sync.retry_base_delay_millis", comment = "Initial delay between transient nickname retries")
    public int nicknameRetryBaseDelayMillis = 500;

    @CfgKey(value = "limits.max_links", comment = "Maximum Discord account links loaded into the bounded runtime index")
    public int maxLinks = 100_000;

    @CfgKey(value = "diagnostics.transition_logs", comment = "Log state transitions and failures without message content or identifiers")
    public boolean diagnosticTransitionLogs = true;

    public DiscordConfig() {
        super(CommunicationsConfigBootstrap.DISCORD_FILE);
    }

    public DiscordSettings settings(Map<String, String> environment) {
        return DiscordSettings.from(this, environment);
    }

    @Override
    protected boolean strictTypeValidation() {
        return true;
    }

    @Override
    public synchronized void reloadCfg() {
        super.reloadCfg();
        boolean removedGeneratedSecret = false;
        if ((botToken == null || botToken.isBlank()) && contains("bot.token")) {
            setNodeValue(node("bot.token"), null);
            removedGeneratedSecret = true;
        }
        if ((webhookUrl == null || webhookUrl.isBlank()) && contains("webhook.chat_url")) {
            setNodeValue(node("webhook.chat_url"), null);
            removedGeneratedSecret = true;
        }
        if ((eventsWebhookUrl == null || eventsWebhookUrl.isBlank()) && contains("webhook.announcements_url")) {
            setNodeValue(node("webhook.announcements_url"), null);
            removedGeneratedSecret = true;
        }
        if (removedGeneratedSecret) {
            if (node("bot").childrenMap().isEmpty()) {
                setNodeValue(node("bot"), null);
            }
            if (node("webhook").childrenMap().isEmpty()) {
                setNodeValue(node("webhook"), null);
            }
            save();
        }
    }

    @Override
    protected boolean shouldWriteDefault(String basePath, Object defaultValue, CommentedConfigurationNode yaml) {
        return !"bot.token".equals(basePath)
                && !"webhook.chat_url".equals(basePath)
                && !"webhook.announcements_url".equals(basePath)
                && !INTERNAL_DEFAULTS.contains(basePath);
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

    private static Map<String, Object> embed(boolean enabled, String title, String description, String color) {
        return defaults(
                entry("enabled", enabled),
                entry("title", title),
                entry("description", description),
                entry("color", color),
                entry("thumbnail_url", ""),
                entry("footer", ""),
                entry("image_url", "")
        );
    }
}
