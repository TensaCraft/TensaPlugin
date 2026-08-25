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

public final class DiscordConfig extends ConfigBase {
    @CfgKey(value = "config_version", comment = "Communications Discord configuration schema version")
    public int configVersion = CommunicationsConfigBootstrap.CONFIG_VERSION;

    @CfgKey(value = "bot.enabled", comment = "Enable the Discord bot, linking, relay and announcements inside Communications")
    public boolean enabled = false;

    @CfgKey(value = "proxy_chat", comment = "Proxy-wide player chat transport and Discord-to-Minecraft display settings")
    public Map<String, Object> proxyChat = defaults(
            entry("enabled", true),
            entry("excluded_servers", List.of("auth", "aero-auth")),
            entry("server_aliases", defaults(
                    entry("aeronautics", "Aeronautics"),
                    entry("creative", "Creative")
            )),
            entry("discord_format", "<gradient:#55FFFF:#C792EA>✦ Discord</gradient> <#667085>•</#667085> <#F4C15D>{player}</#F4C15D> <#667085>›</#667085> <#F4F7FF>{message}</#F4F7FF>")
    );

    @CfgKey(value = "embeds", comment = "Validated Discord event presentation templates")
    public Map<String, Object> embeds = defaults(
            entry("join", embed(false, "Гравець приєднався", "🟢 {player} приєднався до «{server}».", "#57F287")),
            entry("quit", embed(false, "Гравець вийшов", "⚪ {player} вийшов із «{server}».", "#95A5A6")),
            entry("server_switch", embed(false, "Перехід між серверами", "🔄 {player}: «{from}» → «{to}».", "#5865F2")),
            entry("advancement", embed(false, "Нове досягнення", "🏆 {player} отримав досягнення «{advancement}» на «{server}».", "#FEE75C")),
            entry("death", embed(false, "Смерть гравця", "💀 {message}", "#ED4245")),
            entry("link_success", embed(true, "Прив'язку завершено", "Акаунт успішно прив'язано до {player}.", "#57F287")),
            entry("link_error", embed(true, "Не вдалося прив'язати акаунт", "{message}", "#ED4245")),
            entry("backend_unavailable", embed(false, "Сервер недоступний", "🔴 «{server}» тимчасово недоступний.", "#ED4245")),
            entry("backend_recovered", embed(false, "Сервер знову доступний", "🟢 «{server}» знову доступний.", "#57F287"))
    );

    @CfgKey(value = "bot.token", comment = "Secret. Prefer TENSA_DISCORD_BOT_TOKEN; this key is never generated automatically")
    public String botToken = "";

    @CfgKey(value = "webhook.chat_url", comment = "Secret. Prefer TENSA_DISCORD_WEBHOOK_URL; this key is never generated automatically")
    public String webhookUrl = "";

    @CfgKey(value = "discord_ids.guild_id", comment = "Single Discord guild used by relay and account linking")
    public String guildId = "";

    @CfgKey(value = "discord_ids.main_channel_id", comment = "Single Discord text channel used by the global chat relay")
    public String channelId = "";

    @CfgKey(value = "discord_ids.announcements_channel_id", comment = "Optional Discord channel for events; blank uses main_channel_id")
    public String eventsChannelId = "";

    @CfgKey(value = "discord_ids.linked_role_id", comment = "Optional role granted to linked Discord accounts")
    public String linkedRoleId = "";

    @CfgKey(value = "relay.minecraft_to_discord.enabled", comment = "Relay successful global proxy-chat messages to Discord")
    public boolean minecraftToDiscord = true;

    @CfgKey(value = "relay.discord_to_minecraft.enabled", comment = "Relay messages from the configured Discord channel to global proxy chat")
    public boolean discordToMinecraft = true;

    @CfgKey(value = "relay.minecraft_to_discord.format", comment = "Plain Discord message template")
    public String minecraftToDiscordFormat = "{message}";

    @CfgKey(value = "relay.minecraft_to_discord.channels", comment = "Logical Minecraft channels forwarded through the global chat webhook")
    public List<String> minecraftToDiscordChannels = new ArrayList<>(List.of("global", "alert"));

    @CfgKey(value = "achievements.enabled", comment = "Accept advancement events from the configured backend bridge")
    public boolean advancementMessages = false;

    @CfgKey(value = "announcements.servers.include", comment = "Backend names eligible for events; empty selects every registered backend")
    public List<String> includedServers = new ArrayList<>();

    @CfgKey(value = "announcements.servers.exclude", comment = "Backend names never shown in Discord events")
    public List<String> excludedServers = new ArrayList<>(List.of("aero-auth"));

    @CfgKey(value = "announcements.servers.labels", comment = "Player-facing labels keyed by Velocity backend name")
    public Map<String, Object> serverLabels = new LinkedHashMap<>();

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
        boolean changed = removeObsoleteSettings();
        if ((botToken == null || botToken.isBlank()) && contains("bot.token")) {
            setNodeValue(node("bot.token"), null);
            changed = true;
        }
        if ((webhookUrl == null || webhookUrl.isBlank()) && contains("webhook.chat_url")) {
            setNodeValue(node("webhook.chat_url"), null);
            changed = true;
        }
        if (changed) {
            if (node("bot").childrenMap().isEmpty()) {
                setNodeValue(node("bot"), null);
            }
            if (node("webhook").childrenMap().isEmpty()) {
                setNodeValue(node("webhook"), null);
            }
            if (node("linking").childrenMap().isEmpty()) {
                setNodeValue(node("linking"), null);
            }
            save();
        }
    }

    private boolean removeObsoleteSettings() {
        boolean changed = false;
        for (String path : List.of(
                "webhook.enabled",
                "webhook.announcements_url",
                "proxy_chat.webhook",
                "proxy_chat.max_length",
                "proxy_chat.cooldown_millis",
                "proxy_chat.duplicate_window_millis",
                "proxy_chat.max_repeated_characters",
                "proxy_chat.format",
                "proxy_chat.cooldown_message",
                "proxy_chat.duplicate_message",
                "proxy_chat.require_link_to_relay",
                "relay.minecraft_to_discord.avatar_url_template",
                "announcements.backend_status",
                "achievements.backend_bridge",
                "achievements.dedup_seconds",
                "linking.code_ttl_seconds",
                "linking.code_length",
                "linking.command_name",
                "linking.executor_queue_capacity",
                "linking.post_link_queue_capacity",
                "linking.link_announcement",
                "linking.nickname_sync",
                "limits",
                "delivery",
                "gateway",
                "scheduler",
                "diagnostics"
        )) {
            if (contains(path)) {
                setNodeValue(node(path), null);
                changed = true;
            }
        }
        for (String embed : List.of(
                "join", "quit", "server_switch", "advancement", "death", "link_success", "link_error",
                "backend_unavailable", "backend_recovered")) {
            for (String obsolete : List.of("thumbnail_url", "image_url")) {
                String path = "embeds." + embed + "." + obsolete;
                if (contains(path)) {
                    setNodeValue(node(path), null);
                    changed = true;
                }
            }
        }
        return changed;
    }

    @Override
    protected boolean shouldWriteDefault(String basePath, Object defaultValue, CommentedConfigurationNode yaml) {
        return !"bot.token".equals(basePath)
                && !"webhook.chat_url".equals(basePath)
                ;
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
                entry("footer", "")
        );
    }
}
