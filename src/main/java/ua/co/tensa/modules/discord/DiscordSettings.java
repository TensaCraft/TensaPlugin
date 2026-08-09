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
    private static final String PLAYER_AVATAR_URL = "https://mc-heads.net/avatar/{player}/128";

    private static final Pattern SNOWFLAKE = Pattern.compile("[1-9][0-9]{16,19}");
    private static final Pattern SERVER_NAME = Pattern.compile("[a-zA-Z0-9_.-]{1,64}");
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
    private final Set<String> minecraftToDiscordChannels;
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

        this.credentials = new DiscordCredentials(
                requiredSecret(token, TOKEN_ENV),
                parseWebhook(webhook)
        );
        this.guildId = requireSnowflake(config.guildId, "discord_ids.guild_id");
        this.channelId = requireSnowflake(config.channelId, "discord_ids.main_channel_id");
        this.eventsChannelId = optionalSnowflake(config.eventsChannelId, "discord_ids.announcements_channel_id");
        this.linkedRoleId = optionalSnowflake(config.linkedRoleId, "discord_ids.linked_role_id");
        this.linkCommandName = "link";
        this.minecraftToDiscord = config.minecraftToDiscord;
        this.discordToMinecraft = config.discordToMinecraft;
        this.minecraftToDiscordChannels = Set.copyOf(normalizeChannelList(config.minecraftToDiscordChannels));
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
        this.avatarUrlTemplate = PLAYER_AVATAR_URL;
        this.maxMinecraftMessageLength = 256;
        this.maxDiscordMessageLength = 1_000;
        this.queueCapacity = 256;
        this.eventRatePerMinute = 60;
        this.eventStateCapacity = 4_096;
        this.deliveryAttempts = 3;
        this.deliveryTimeout = Duration.ofSeconds(10);
        this.linkCodeTtl = Duration.ofMinutes(10);
        this.linkCodeLength = 8;
        this.linkExecutorCapacity = 32;
        this.maxLinks = 100_000;
        this.reconnectMaxDelaySeconds = 120;
        this.backendStatusPollInterval = Duration.ofSeconds(15);
        this.backendStatusPingTimeout = Duration.ofSeconds(5);
        this.backendStatusDebounce = Duration.ofSeconds(20);
        this.backendStatusConfirmations = 2;
        this.maxMonitoredServers = 128;
        this.backendEventChannel = "tensa:discord_events";
        this.advancementDedupWindow = Duration.ofSeconds(10);
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
    public Set<String> minecraftToDiscordChannels() { return minecraftToDiscordChannels; }
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

    private static List<String> normalizeChannelList(List<String> values) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                String channel = trim(value);
                if (!SERVER_NAME.matcher(channel).matches()) {
                    throw new DiscordConfigurationException(
                            "discord.yml relay.minecraft_to_discord.channels contains an invalid logical channel");
                }
                normalized.add(channel.toLowerCase(Locale.ROOT));
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

    private static String requireTemplate(String value, String key) {
        String trimmed = trim(value);
        if (trimmed.isBlank() || trimmed.length() > 2_000) {
            throw new DiscordConfigurationException("discord.yml " + key + " must be a non-empty template under 2000 characters");
        }
        return trimmed;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
