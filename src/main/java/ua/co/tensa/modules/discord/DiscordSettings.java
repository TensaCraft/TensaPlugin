package ua.co.tensa.modules.discord;

import ua.co.tensa.modules.discord.data.DiscordConfig;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import ua.co.tensa.modules.runtime.ModuleScheduler;

public final class DiscordSettings {
    public static final String TOKEN_ENV = "TENSA_DISCORD_BOT_TOKEN";
    public static final String WEBHOOK_ENV = "TENSA_DISCORD_WEBHOOK_URL";
    public static final String EVENTS_WEBHOOK_ENV = "TENSA_DISCORD_EVENTS_WEBHOOK_URL";

    private static final Pattern SNOWFLAKE = Pattern.compile("[1-9][0-9]{16,19}");
    private static final Pattern COMMAND_NAME = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final Pattern SERVER_NAME = Pattern.compile("[a-zA-Z0-9_.-]{1,64}");
    private static final Pattern CHANNEL_NAME = Pattern.compile("[a-z0-9_.-]{1,32}:[a-z0-9_./-]{1,32}");
    private static final Set<String> DISCORD_WEBHOOK_HOSTS = Set.of(
            "discord.com", "ptb.discord.com", "canary.discord.com", "discordapp.com"
    );

    private final DiscordCredentials credentials;
    private final String guildId;
    private final String channelId;
    private final String eventsChannelId;
    private final String linkedRoleId;
    private final String linkCommandName;
    private final boolean webhookAutoCreate;
    private final String webhookName;
    private final boolean minecraftToDiscord;
    private final boolean discordToMinecraft;
    private final boolean joinMessages;
    private final boolean quitMessages;
    private final boolean serverSwitchMessages;
    private final boolean backendStatusMessages;
    private final boolean advancementMessages;
    private final List<String> includedServers;
    private final Set<String> excludedServers;
    private final Map<String, String> serverLabels;
    private final String minecraftToDiscordFormat;
    private final String joinFormat;
    private final String quitFormat;
    private final String serverSwitchFormat;
    private final String backendUnavailableFormat;
    private final String backendRecoveredFormat;
    private final String advancementFormat;
    private final DiscordEmbedTemplate joinEmbed;
    private final DiscordEmbedTemplate quitEmbed;
    private final DiscordEmbedTemplate serverSwitchEmbed;
    private final DiscordEmbedTemplate advancementEmbed;
    private final DiscordEmbedTemplate linkSuccessEmbed;
    private final DiscordEmbedTemplate linkErrorEmbed;
    private final DiscordEmbedTemplate backendUnavailableEmbed;
    private final DiscordEmbedTemplate backendRecoveredEmbed;
    private final String avatarUrlTemplate;
    private final int maxMinecraftMessageLength;
    private final int maxDiscordMessageLength;
    private final int queueCapacity;
    private final int eventRatePerMinute;
    private final int eventStateCapacity;
    private final int deliveryAttempts;
    private final Duration deliveryTimeout;
    private final Duration linkCodeTtl;
    private final int linkCodeLength;
    private final int linkExecutorCapacity;
    private final int postLinkQueueCapacity;
    private final boolean linkAnnouncementEnabled;
    private final String linkAnnouncementFormat;
    private final List<String> linkAnnouncementIncludedServers;
    private final Set<String> linkAnnouncementExcludedServers;
    private final boolean nicknameSync;
    private final int nicknameSyncAttempts;
    private final Duration nicknameRetryBaseDelay;
    private final DiscordRelayGuard relayGuard;
    private final int maxLinks;
    private final int reconnectMaxDelaySeconds;
    private final Duration backendStatusPollInterval;
    private final Duration backendStatusPingTimeout;
    private final Duration backendStatusDebounce;
    private final int backendStatusConfirmations;
    private final int maxMonitoredServers;
    private final String backendEventChannel;
    private final Duration advancementDedupWindow;
    private final ModuleScheduler.Defaults schedulerDefaults;

    private DiscordSettings(DiscordConfig config, Map<String, String> environment) {
        String token = environmentOverride(environment, TOKEN_ENV, config.botToken);
        String webhook = config.webhookEnabled
                ? environmentOverride(environment, WEBHOOK_ENV, config.webhookUrl)
                : "";
        String eventsWebhook = config.webhookEnabled
                ? environmentOverride(environment, EVENTS_WEBHOOK_ENV, config.eventsWebhookUrl)
                : "";

        this.credentials = new DiscordCredentials(
                requiredSecret(token, TOKEN_ENV),
                parseWebhook(webhook),
                parseWebhook(eventsWebhook)
        );
        this.guildId = requireSnowflake(config.guildId, "discord_ids.guild_id");
        this.channelId = requireSnowflake(config.channelId, "discord_ids.main_channel_id");
        this.eventsChannelId = optionalSnowflake(config.eventsChannelId, "discord_ids.announcements_channel_id");
        this.linkedRoleId = optionalSnowflake(config.linkedRoleId, "discord_ids.linked_role_id");
        this.linkCommandName = validateCommandName(config.linkCommandName);
        this.webhookAutoCreate = config.webhookEnabled && config.webhookAutoCreate;
        this.webhookName = validateWebhookName(config.webhookName);
        this.minecraftToDiscord = config.minecraftToDiscord;
        this.discordToMinecraft = config.discordToMinecraft;
        this.joinEmbed = embed(config.embeds, "join", Set.of("player", "server"));
        this.quitEmbed = embed(config.embeds, "quit", Set.of("player", "server"));
        this.serverSwitchEmbed = embed(config.embeds, "server_switch", Set.of("player", "from", "to"));
        this.advancementEmbed = embed(config.embeds, "advancement", Set.of(
                "player", "server", "advancement", "description"));
        this.linkSuccessEmbed = embed(config.embeds, "link_success", Set.of("player"));
        this.linkErrorEmbed = embed(config.embeds, "link_error", Set.of("message"));
        this.backendUnavailableEmbed = embed(config.embeds, "backend_unavailable", Set.of("server"));
        this.backendRecoveredEmbed = embed(config.embeds, "backend_recovered", Set.of("server"));
        this.joinMessages = joinEmbed.enabled();
        this.quitMessages = quitEmbed.enabled();
        this.serverSwitchMessages = serverSwitchEmbed.enabled();
        this.backendStatusMessages = backendUnavailableEmbed.enabled() || backendRecoveredEmbed.enabled();
        this.advancementMessages = config.advancementMessages;
        this.includedServers = normalizeServerList(config.includedServers, "announcements.servers.include");
        this.excludedServers = Set.copyOf(normalizeServerList(config.excludedServers, "announcements.servers.exclude"));
        this.serverLabels = validateServerLabels(config.serverLabels);
        this.minecraftToDiscordFormat = requireTemplate(config.minecraftToDiscordFormat, "relay.minecraft_to_discord.format");
        this.joinFormat = joinEmbed.description();
        this.quitFormat = quitEmbed.description();
        this.serverSwitchFormat = serverSwitchEmbed.description();
        this.backendUnavailableFormat = backendUnavailableEmbed.description();
        this.backendRecoveredFormat = backendRecoveredEmbed.description();
        this.advancementFormat = advancementEmbed.description();
        this.avatarUrlTemplate = validateAvatarTemplate(config.avatarUrlTemplate);
        this.maxMinecraftMessageLength = bounded(config.maxMinecraftMessageLength, 32, 1_000, "limits.max_minecraft_message_length");
        this.maxDiscordMessageLength = bounded(config.maxDiscordMessageLength, 32, 1_900, "limits.max_discord_message_length");
        this.queueCapacity = bounded(config.queueCapacity, 8, 4_096, "limits.queue_capacity");
        this.eventRatePerMinute = bounded(config.eventRatePerMinute, 1, 600, "limits.event_rate_per_minute");
        this.eventStateCapacity = bounded(config.eventStateCapacity, 32, 16_384, "limits.event_state_capacity");
        this.deliveryAttempts = bounded(config.deliveryAttempts, 1, 5, "delivery.attempts");
        this.deliveryTimeout = Duration.ofSeconds(bounded(config.deliveryTimeoutSeconds, 2, 30, "delivery.timeout_seconds"));
        this.linkCodeTtl = Duration.ofSeconds(bounded(config.linkCodeTtlSeconds, 30, 3_600, "linking.code_ttl_seconds"));
        this.linkCodeLength = bounded(config.linkCodeLength, 6, 12, "linking.code_length");
        this.linkExecutorCapacity = bounded(config.linkExecutorCapacity, 8, 256, "linking.executor_queue_capacity");
        this.postLinkQueueCapacity = bounded(config.postLinkQueueCapacity, 8, 256, "linking.post_link_queue_capacity");
        this.linkAnnouncementEnabled = config.linkAnnouncementEnabled;
        this.linkAnnouncementFormat = requireTemplate(config.linkAnnouncementFormat, "linking.link_announcement.format");
        this.linkAnnouncementIncludedServers = normalizeServerList(
                config.linkAnnouncementIncludedServers, "linking.link_announcement.servers.include");
        this.linkAnnouncementExcludedServers = Set.copyOf(normalizeServerList(
                config.linkAnnouncementExcludedServers, "linking.link_announcement.servers.exclude"));
        this.nicknameSync = validateNicknameMode(config.nicknameSyncMode);
        this.nicknameSyncAttempts = bounded(config.nicknameSyncAttempts, 1, 5, "linking.nickname_sync.attempts");
        this.nicknameRetryBaseDelay = Duration.ofMillis(bounded(
                config.nicknameRetryBaseDelayMillis, 100, 5_000, "linking.nickname_sync.retry_base_delay_millis"));
        this.relayGuard = validateRelayGuard(config.proxyChat);
        this.maxLinks = bounded(config.maxLinks, 1, 100_000, "limits.max_links");
        this.reconnectMaxDelaySeconds = bounded(config.reconnectMaxDelaySeconds, 5, 900, "gateway.max_reconnect_delay_seconds");
        this.backendStatusPollInterval = Duration.ofSeconds(bounded(
                config.backendStatusPollIntervalSeconds, 5, 300, "announcements.backend_status.poll_interval_seconds"));
        this.backendStatusPingTimeout = Duration.ofSeconds(bounded(
                config.backendStatusPingTimeoutSeconds, 1, 30, "announcements.backend_status.ping_timeout_seconds"));
        this.backendStatusDebounce = Duration.ofSeconds(bounded(
                config.backendStatusDebounceSeconds, 0, 300, "announcements.backend_status.debounce_seconds"));
        this.backendStatusConfirmations = bounded(
                config.backendStatusConfirmations, 1, 10, "announcements.backend_status.confirmations");
        this.maxMonitoredServers = bounded(
                config.maxMonitoredServers, 1, 512, "announcements.backend_status.max_monitored_servers");
        this.backendEventChannel = validateBackendChannel(config.backendEventChannel);
        this.advancementDedupWindow = Duration.ofSeconds(bounded(
                config.advancementDedupSeconds, 1, 300, "achievements.dedup_seconds"));
        int schedulerBackoffMillis = bounded(config.schedulerBackoffMillis, 0, 60_000, "scheduler.backoff_millis");
        int schedulerMaxBackoffSeconds = bounded(
                config.schedulerMaxBackoffSeconds, 1, 900, "scheduler.max_backoff_seconds");
        if (Duration.ofSeconds(schedulerMaxBackoffSeconds).toMillis() < schedulerBackoffMillis) {
            throw new DiscordConfigurationException(
                    "discord.yml scheduler.max_backoff_seconds must not be lower than scheduler.backoff_millis");
        }
        this.schedulerDefaults = new ModuleScheduler.Defaults(
                bounded(config.schedulerMaxJobs, 16, 16_384, "scheduler.max_jobs"),
                bounded(config.schedulerWorkerThreads, 1, 8, "scheduler.worker_threads"),
                bounded(config.schedulerQueueCapacity, 8, 4_096, "scheduler.queue_capacity"),
                Duration.ofSeconds(bounded(config.schedulerTimeoutSeconds, 1, 300, "scheduler.timeout_seconds")),
                bounded(config.schedulerMaxAttempts, 1, 10, "scheduler.max_attempts"),
                Duration.ofMillis(schedulerBackoffMillis),
                Duration.ofSeconds(schedulerMaxBackoffSeconds),
                bounded(config.schedulerJitterPercent, 0, 100, "scheduler.jitter_percent") / 100.0
        );
    }

    public static DiscordSettings from(DiscordConfig config, Map<String, String> environment) {
        if (config == null) {
            throw new DiscordConfigurationException("Discord configuration is unavailable");
        }
        return new DiscordSettings(config, environment == null ? Map.of() : environment);
    }

    public DiscordCredentials credentials() { return credentials; }
    public String guildId() { return guildId; }
    public String channelId() { return channelId; }
    public String eventsChannelId() { return eventsChannelId.isBlank() ? channelId : eventsChannelId; }
    public String linkedRoleId() { return linkedRoleId; }
    public String linkCommandName() { return linkCommandName; }
    public boolean webhookAutoCreate() { return webhookAutoCreate; }
    public String webhookName() { return webhookName; }
    public boolean minecraftToDiscord() { return minecraftToDiscord; }
    public boolean discordToMinecraft() { return discordToMinecraft; }
    public boolean joinMessages() { return joinMessages; }
    public boolean quitMessages() { return quitMessages; }
    public boolean serverSwitchMessages() { return serverSwitchMessages; }
    public boolean backendStatusMessages() { return backendStatusMessages; }
    public boolean advancementMessages() { return advancementMessages; }
    public List<String> includedServers() { return includedServers; }
    public Set<String> excludedServers() { return excludedServers; }
    public Map<String, String> serverLabels() { return serverLabels; }
    public String minecraftToDiscordFormat() { return minecraftToDiscordFormat; }
    public String joinFormat() { return joinFormat; }
    public String quitFormat() { return quitFormat; }
    public String serverSwitchFormat() { return serverSwitchFormat; }
    public String backendUnavailableFormat() { return backendUnavailableFormat; }
    public String backendRecoveredFormat() { return backendRecoveredFormat; }
    public String advancementFormat() { return advancementFormat; }
    DiscordEmbedTemplate joinEmbed() { return joinEmbed; }
    DiscordEmbedTemplate quitEmbed() { return quitEmbed; }
    DiscordEmbedTemplate serverSwitchEmbed() { return serverSwitchEmbed; }
    DiscordEmbedTemplate advancementEmbed() { return advancementEmbed; }
    DiscordEmbedTemplate linkSuccessEmbed() { return linkSuccessEmbed; }
    DiscordEmbedTemplate linkErrorEmbed() { return linkErrorEmbed; }
    DiscordEmbedTemplate backendUnavailableEmbed() { return backendUnavailableEmbed; }
    DiscordEmbedTemplate backendRecoveredEmbed() { return backendRecoveredEmbed; }
    public String avatarUrlTemplate() { return avatarUrlTemplate; }
    public int maxMinecraftMessageLength() { return maxMinecraftMessageLength; }
    public int maxDiscordMessageLength() { return maxDiscordMessageLength; }
    public int queueCapacity() { return queueCapacity; }
    public int eventRatePerMinute() { return eventRatePerMinute; }
    public int eventStateCapacity() { return eventStateCapacity; }
    public int deliveryAttempts() { return deliveryAttempts; }
    public Duration deliveryTimeout() { return deliveryTimeout; }
    public Duration linkCodeTtl() { return linkCodeTtl; }
    public int linkCodeLength() { return linkCodeLength; }
    public int linkExecutorCapacity() { return linkExecutorCapacity; }
    public int postLinkQueueCapacity() { return postLinkQueueCapacity; }
    public boolean linkAnnouncementEnabled() { return linkAnnouncementEnabled; }
    public String linkAnnouncementFormat() { return linkAnnouncementFormat; }
    public boolean nicknameSync() { return nicknameSync; }
    public int nicknameSyncAttempts() { return nicknameSyncAttempts; }
    public Duration nicknameRetryBaseDelay() { return nicknameRetryBaseDelay; }
    public DiscordRelayGuard relayGuard() { return relayGuard; }
    public int maxLinks() { return maxLinks; }
    public int reconnectMaxDelaySeconds() { return reconnectMaxDelaySeconds; }
    public Duration backendStatusPollInterval() { return backendStatusPollInterval; }
    public Duration backendStatusPingTimeout() { return backendStatusPingTimeout; }
    public Duration backendStatusDebounce() { return backendStatusDebounce; }
    public int backendStatusConfirmations() { return backendStatusConfirmations; }
    public int maxMonitoredServers() { return maxMonitoredServers; }
    public String backendEventChannel() { return backendEventChannel; }
    public Duration advancementDedupWindow() { return advancementDedupWindow; }
    public ModuleScheduler.Defaults schedulerDefaults() { return schedulerDefaults; }

    public boolean linkAnnouncementAllows(String serverName) {
        String normalized = trim(serverName).toLowerCase(Locale.ROOT);
        if (linkAnnouncementExcludedServers.contains(normalized)) {
            return false;
        }
        return linkAnnouncementIncludedServers.isEmpty() || linkAnnouncementIncludedServers.contains(normalized);
    }

    @Override
    public String toString() {
        return "DiscordSettings[guildId=" + guildId
                + ", channelId=" + channelId
                + ", eventsChannelId=" + eventsChannelId()
                + ", linkedRoleConfigured=" + !linkedRoleId.isBlank()
                + ", linkCommandName=" + linkCommandName
                + ", credentials=" + credentials + "]";
    }

    private static String environmentOverride(Map<String, String> environment, String key, String configured) {
        String fromEnvironment = environment.get(key);
        return fromEnvironment == null || fromEnvironment.isBlank() ? trim(configured) : fromEnvironment.trim();
    }

    private static String requiredSecret(String value, String environmentKey) {
        String trimmed = trim(value);
        if (trimmed.isBlank()) {
            throw new DiscordConfigurationException("Discord bot token is missing; configure it or set " + environmentKey);
        }
        return trimmed;
    }

    private static URI parseWebhook(String value) {
        String trimmed = trim(value);
        if (trimmed.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(trimmed);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !DISCORD_WEBHOOK_HOSTS.contains(host)
                    || uri.getPath() == null
                    || !uri.getPath().startsWith("/api/webhooks/")) {
                throw new DiscordConfigurationException("Discord webhook URL must be an HTTPS Discord webhook URL");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new DiscordConfigurationException("Discord webhook URL is invalid");
        }
    }

    private static String requireSnowflake(String value, String key) {
        String trimmed = trim(value);
        if (!SNOWFLAKE.matcher(trimmed).matches()) {
            throw new DiscordConfigurationException("discord.yml " + key + " must be a Discord snowflake ID");
        }
        return trimmed;
    }

    private static String optionalSnowflake(String value, String key) {
        String trimmed = trim(value);
        return trimmed.isBlank() ? "" : requireSnowflake(trimmed, key);
    }

    private static String validateCommandName(String value) {
        String command = trim(value).toLowerCase(Locale.ROOT);
        if (!COMMAND_NAME.matcher(command).matches()) {
            throw new DiscordConfigurationException("discord.yml linking.command_name must match [a-z0-9_-]{1,32}");
        }
        return command;
    }

    private static String validateWebhookName(String value) {
        String name = DiscordSanitizer.normalize(value).trim();
        if (name.length() < 2 || name.length() > 80) {
            throw new DiscordConfigurationException("discord.yml proxy_chat.webhook.name must contain 2-80 characters");
        }
        return name;
    }

    private static String validateAvatarTemplate(String value) {
        String template = requireTemplate(value, "relay.minecraft_to_discord.avatar_url_template");
        String sample = template.replace("{uuid}", "00000000000000000000000000000000")
                .replace("{player}", "Player");
        try {
            URI uri = new URI(sample);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new DiscordConfigurationException("discord.yml relay.minecraft_to_discord.avatar_url_template must be an HTTPS URL");
            }
        } catch (URISyntaxException e) {
            throw new DiscordConfigurationException("discord.yml relay.minecraft_to_discord.avatar_url_template is invalid");
        }
        return template;
    }

    private static List<String> normalizeServerList(List<String> values, String key) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                String server = trim(value);
                if (!SERVER_NAME.matcher(server).matches()) {
                    throw new DiscordConfigurationException("discord.yml " + key + " contains an invalid backend name");
                }
                normalized.add(server.toLowerCase(Locale.ROOT));
            }
        }
        return List.copyOf(normalized);
    }

    private static Map<String, String> validateServerLabels(Map<String, Object> values) {
        LinkedHashMap<String, String> labels = new LinkedHashMap<>();
        if (values == null) {
            return Map.of();
        }
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String server = trim(entry.getKey());
            if (!SERVER_NAME.matcher(server).matches() || !(entry.getValue() instanceof String)) {
                throw new DiscordConfigurationException("discord.yml announcements.servers.labels must map backend names to text");
            }
            String label = DiscordSanitizer.normalize(entry.getValue().toString()).trim();
            if (label.isBlank() || label.codePointCount(0, label.length()) > 80) {
                throw new DiscordConfigurationException("discord.yml announcements.servers.labels values must contain 1-80 characters");
            }
            labels.put(server.toLowerCase(Locale.ROOT), label);
        }
        return Map.copyOf(labels);
    }

    private static String validateBackendChannel(String value) {
        String channel = trim(value).toLowerCase(Locale.ROOT);
        if (!CHANNEL_NAME.matcher(channel).matches()) {
            throw new DiscordConfigurationException("discord.yml achievements.backend_bridge.channel must be a namespaced channel");
        }
        return channel;
    }

    private static boolean validateNicknameMode(String value) {
        return switch (trim(value).toLowerCase(Locale.ROOT)) {
            case "disabled" -> false;
            case "minecraft" -> true;
            default -> throw new DiscordConfigurationException(
                    "discord.yml linking.nickname_sync.mode must be disabled or minecraft");
        };
    }

    private static DiscordRelayGuard validateRelayGuard(Map<String, Object> proxyChat) {
        Map<String, Object> guard = childMap(proxyChat, "require_link_to_relay");
        boolean enabled = booleanValue(guard.get("enabled"), false);
        java.util.EnumSet<DiscordRelayGuard.Direction> directions = java.util.EnumSet.noneOf(
                DiscordRelayGuard.Direction.class);
        for (String value : stringList(guard.get("directions"), List.of(
                "minecraft_to_discord", "discord_to_minecraft"))) {
            directions.add(DiscordRelayGuard.Direction.parse(value));
        }
        if (directions.isEmpty()) {
            throw new DiscordConfigurationException(
                    "discord.yml proxy_chat.require_link_to_relay.directions must not be empty");
        }
        LinkedHashSet<String> channels = new LinkedHashSet<>();
        for (String value : stringList(guard.get("channels"), List.of("global"))) {
            String channel = trim(value).toLowerCase(Locale.ROOT);
            if (!COMMAND_NAME.matcher(channel).matches()) {
                throw new DiscordConfigurationException(
                        "discord.yml proxy_chat.require_link_to_relay.channels contains an invalid logical channel");
            }
            channels.add(channel);
        }
        if (channels.isEmpty()) {
            throw new DiscordConfigurationException(
                    "discord.yml proxy_chat.require_link_to_relay.channels must not be empty");
        }
        String minecraftMessage = requireTemplate(
                stringValue(guard.get("minecraft_message"), "<yellow>Прив'яжіть Discord через /discord link.</yellow>"),
                "proxy_chat.require_link_to_relay.minecraft_message");
        String discordReply = DiscordSanitizer.normalize(stringValue(
                guard.get("discord_reply"), "Прив'яжіть Minecraft-акаунт через /discord link у грі.")).trim();
        if (discordReply.isBlank() || discordReply.length() > 2_000) {
            throw new DiscordConfigurationException(
                    "discord.yml proxy_chat.require_link_to_relay.discord_reply must contain 1-2000 characters");
        }
        int deleteSeconds = bounded(intValue(guard.get("reply_delete_after_seconds"), 10), 1, 60,
                "proxy_chat.require_link_to_relay.reply_delete_after_seconds");
        int cooldownSeconds = bounded(intValue(guard.get("feedback_cooldown_seconds"), 30), 1, 600,
                "proxy_chat.require_link_to_relay.feedback_cooldown_seconds");
        return new DiscordRelayGuard(
                enabled,
                directions,
                DiscordRelayGuard.Mode.parse(stringValue(guard.get("mode"), "only")),
                channels,
                minecraftMessage,
                discordReply,
                Duration.ofSeconds(deleteSeconds),
                Duration.ofSeconds(cooldownSeconds)
        );
    }

    private static DiscordEmbedTemplate embed(
            Map<String, Object> embeds,
            String name,
            Set<String> allowedPlaceholders
    ) {
        Map<String, Object> raw = childMap(embeds, name);
        if (raw.isEmpty()) {
            throw new DiscordConfigurationException("discord.yml embeds." + name + " is required");
        }
        return DiscordEmbedTemplate.parse("embeds." + name, raw, allowedPlaceholders);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> childMap(Map<String, Object> parent, String key) {
        if (parent == null || !(parent.get(key) instanceof Map<?, ?> values)) {
            return Map.of();
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        values.forEach((childKey, value) -> result.put(String.valueOf(childKey), value));
        return result;
    }

    private static List<String> stringList(Object value, List<String> fallback) {
        if (!(value instanceof Iterable<?> values)) {
            return fallback;
        }
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        values.forEach(item -> result.add(String.valueOf(item)));
        return result;
    }

    private static String stringValue(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static int intValue(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            throw new DiscordConfigurationException(
                    "discord.yml proxy_chat.require_link_to_relay contains a non-numeric duration");
        }
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if ("true".equals(text) || "false".equals(text)) {
            return Boolean.parseBoolean(text);
        }
        throw new DiscordConfigurationException(
                "discord.yml proxy_chat.require_link_to_relay.enabled must be true or false");
    }

    private static String requireTemplate(String value, String key) {
        String trimmed = trim(value);
        if (trimmed.isBlank() || trimmed.length() > 2_000) {
            throw new DiscordConfigurationException("discord.yml " + key + " must be a non-empty template under 2000 characters");
        }
        return trimmed;
    }

    private static int bounded(int value, int minimum, int maximum, String key) {
        if (value < minimum || value > maximum) {
            throw new DiscordConfigurationException("discord.yml " + key + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
