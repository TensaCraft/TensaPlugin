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
    private final int maxLinks;
    private final int reconnectMaxDelaySeconds;
    private final Duration backendStatusPollInterval;
    private final Duration backendStatusPingTimeout;
    private final Duration backendStatusDebounce;
    private final int backendStatusConfirmations;
    private final int maxMonitoredServers;
    private final String backendEventChannel;
    private final Duration advancementDedupWindow;

    private DiscordSettings(DiscordConfig config, Map<String, String> environment) {
        String token = environmentOverride(environment, TOKEN_ENV, config.botToken);
        String webhook = environmentOverride(environment, WEBHOOK_ENV, config.webhookUrl);
        String eventsWebhook = environmentOverride(environment, EVENTS_WEBHOOK_ENV, config.eventsWebhookUrl);

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
        this.minecraftToDiscord = config.minecraftToDiscord;
        this.discordToMinecraft = config.discordToMinecraft;
        this.joinMessages = config.joinMessages;
        this.quitMessages = config.quitMessages;
        this.serverSwitchMessages = config.serverSwitchMessages;
        this.backendStatusMessages = config.backendStatusMessages;
        this.advancementMessages = config.advancementMessages;
        this.includedServers = normalizeServerList(config.includedServers, "announcements.servers.include");
        this.excludedServers = Set.copyOf(normalizeServerList(config.excludedServers, "announcements.servers.exclude"));
        this.serverLabels = validateServerLabels(config.serverLabels);
        this.minecraftToDiscordFormat = requireTemplate(config.minecraftToDiscordFormat, "relay.minecraft_to_discord.format");
        this.joinFormat = requireTemplate(config.joinFormat, "announcements.join.format");
        this.quitFormat = requireTemplate(config.quitFormat, "announcements.quit.format");
        this.serverSwitchFormat = requireTemplate(config.serverSwitchFormat, "announcements.server_switch.format");
        this.backendUnavailableFormat = requireTemplate(config.backendUnavailableFormat, "announcements.backend_status.unavailable_format");
        this.backendRecoveredFormat = requireTemplate(config.backendRecoveredFormat, "announcements.backend_status.recovered_format");
        this.advancementFormat = requireTemplate(config.advancementFormat, "achievements.format");
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
    public int maxLinks() { return maxLinks; }
    public int reconnectMaxDelaySeconds() { return reconnectMaxDelaySeconds; }
    public Duration backendStatusPollInterval() { return backendStatusPollInterval; }
    public Duration backendStatusPingTimeout() { return backendStatusPingTimeout; }
    public Duration backendStatusDebounce() { return backendStatusDebounce; }
    public int backendStatusConfirmations() { return backendStatusConfirmations; }
    public int maxMonitoredServers() { return maxMonitoredServers; }
    public String backendEventChannel() { return backendEventChannel; }
    public Duration advancementDedupWindow() { return advancementDedupWindow; }

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
