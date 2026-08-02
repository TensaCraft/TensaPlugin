package ua.co.tensa.modules.discord;

import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import ua.co.tensa.config.model.YamlAdapter;
import ua.co.tensa.config.model.YamlFileIO;
import ua.co.tensa.modules.AbstractModule;
import ua.co.tensa.modules.ModuleEntry;
import ua.co.tensa.modules.chat.ChatCommands;
import ua.co.tensa.modules.chat.ProxyChatListener;
import ua.co.tensa.modules.chat.ProxyChatService;
import ua.co.tensa.modules.chat.data.ChatConfig;
import ua.co.tensa.modules.discord.data.DiscordConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** One lifecycle owner for Minecraft chat, Discord linking, and both relay directions. */
public final class CommunicationsModule extends AbstractModule {
    private static final CommunicationsModule INSTANCE = new CommunicationsModule();
    public static final ModuleEntry ENTRY = INSTANCE;

    private final Object lifecycleLock = new Object();
    private volatile Prepared activePlan;
    private volatile ActiveRuntime activeRuntime;

    private CommunicationsModule() {
        super("communications", "Communications");
    }

    @Override
    protected void onEnable() {
        synchronized (lifecycleLock) {
            Prepared plan = prepare();
            activeRuntime = activate(plan);
            activePlan = plan;
        }
    }

    @Override
    protected void onDisable() {
        synchronized (lifecycleLock) {
            deactivate(activeRuntime);
            activeRuntime = null;
            activePlan = null;
        }
    }

    @Override
    protected void onReload() {
        synchronized (lifecycleLock) {
            // Preparation performs parsing and all semantic validation while
            // the current runtime is still fully operational.
            Prepared nextPlan = prepare();
            Prepared previousPlan = activePlan;
            ActiveRuntime previousRuntime = activeRuntime;

            deactivate(previousRuntime);
            activeRuntime = null;
            try {
                activeRuntime = activate(nextPlan);
                activePlan = nextPlan;
            } catch (RuntimeException replacementFailure) {
                if (previousPlan != null) {
                    try {
                        activeRuntime = activate(previousPlan);
                        activePlan = previousPlan;
                    } catch (RuntimeException rollbackFailure) {
                        replacementFailure.addSuppressed(rollbackFailure);
                    }
                }
                throw new IllegalStateException(
                        "Communications replacement failed; previous validated configuration was restored",
                        replacementFailure
                );
            }
        }
    }

    @Override
    protected boolean restartOnReloadFailure() {
        return false;
    }

    private Prepared prepare() {
        validateYaml(Tensa.pluginPath.resolve("chats.yml"));
        validateYaml(Tensa.pluginPath.resolve("discord.yml"));

        ChatConfig chatConfig = new ChatConfig();
        chatConfig.reloadCfg();
        DiscordConfig discordConfig = new DiscordConfig();
        discordConfig.reloadCfg();

        validateChat(chatConfig.adapter());
        validateProxyChat(discordConfig.adapter());
        DiscordSettings discordSettings = discordConfig.enabled
                ? discordConfig.settings(System.getenv())
                : null;
        if (discordSettings != null) {
            validateLinkStore(discordSettings);
        }
        return new Prepared(chatConfig.adapter(), discordConfig.adapter(), discordSettings);
    }

    private ActiveRuntime activate(Prepared plan) {
        AtomicReference<DiscordRuntime> discordReference = new AtomicReference<>();
        ProxyChatService proxyChat = new ProxyChatService(
                plan.chatConfig(),
                plan.discordConfig(),
                message -> {
                    DiscordRuntime discord = discordReference.get();
                    if (discord != null) {
                        discord.relayMinecraftChat(message);
                    }
                }
        );
        ChatCommands chatCommands = new ChatCommands(plan.chatConfig(), proxyChat);
        ActiveRuntime active = new ActiveRuntime(proxyChat, chatCommands);

        try {
            if (plan.discordSettings() != null) {
                startDiscord(active, plan.discordSettings(), discordReference);
            }
            registerListener(new ProxyChatListener(proxyChat, chatCommands));
            chatCommands.register();
            return active;
        } catch (RuntimeException failure) {
            deactivate(active);
            throw failure;
        }
    }

    private void startDiscord(
            ActiveRuntime active,
            DiscordSettings settings,
            AtomicReference<DiscordRuntime> discordReference
    ) {
        AtomicLinkStore store = new AtomicLinkStore(Tensa.pluginPath, settings.linkStorePath());
        try {
            store.load();
        } catch (IOException exception) {
            throw new IllegalStateException("Discord link store could not be loaded safely", exception);
        }

        JdaDiscordGateway gateway = new JdaDiscordGateway(settings);
        DiscordLinkService linkService = new DiscordLinkService(
                store,
                new LinkCodeRegistry(settings.linkCodeTtl(), settings.linkCodeLength()),
                gateway,
                settings
        );
        DiscordRuntime discord = new DiscordRuntime(
                settings,
                gateway,
                linkService,
                new DiscordDelivery(gateway, new DiscordWebhookClient(settings)),
                active.proxyChat()::publishExternal
        );
        active.discordRuntime = discord;
        discordReference.set(discord);
        discord.start();

        DiscordVelocityListener velocityListener = registerListener(new DiscordVelocityListener(discord, settings));
        Tensa.server.getAllPlayers().forEach(velocityListener::prime);

        if (settings.backendStatusMessages()) {
            active.statusMonitor = new VelocityBackendStatusMonitor(Tensa.server, settings, discord);
            scheduleRepeating(
                    active.statusMonitor::poll,
                    1,
                    settings.backendStatusPollInterval().toSeconds(),
                    TimeUnit.SECONDS
            );
        }
        if (settings.advancementMessages()) {
            VelocityDiscordBackendBridge bridge = new VelocityDiscordBackendBridge(settings, discord);
            active.backendEventBridge = bridge;
            active.backendEventChannel = bridge.channel();
            Tensa.server.getChannelRegistrar().register(active.backendEventChannel);
            registerListener(bridge);
        }
        registerCommand("discord", "", new DiscordCommand(discord));
        active.discordCommandRegistered = true;
    }

    private void deactivate(ActiveRuntime active) {
        if (active == null) {
            return;
        }
        active.chatCommands().unregister();
        if (active.discordCommandRegistered) {
            unregisterCommands("discord");
            active.discordCommandRegistered = false;
        }
        cancelAllTasks();
        unregisterAllListeners();
        if (active.statusMonitor != null) {
            active.statusMonitor.close();
            active.statusMonitor = null;
        }
        if (active.backendEventBridge != null) {
            active.backendEventBridge.close();
            active.backendEventBridge = null;
        }
        if (active.backendEventChannel != null && Tensa.server != null) {
            try {
                Tensa.server.getChannelRegistrar().unregister(active.backendEventChannel);
            } catch (RuntimeException ignored) {
            }
            active.backendEventChannel = null;
        }
        if (active.discordRuntime != null) {
            active.discordRuntime.close();
            active.discordRuntime = null;
        }
        active.proxyChat().clear();
    }

    private static void validateYaml(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try {
            YamlConfigurationLoader loader = YamlFileIO.loader(path);
            YamlFileIO.load(loader);
        } catch (Exception exception) {
            throw new DiscordConfigurationException(
                    path.getFileName() + " could not be parsed: " + DiscordDiagnostics.describe(exception)
            );
        }
    }

    private static void validateChat(YamlAdapter config) {
        Set<String> aliases = new HashSet<>();
        Set<String> reserved = Set.of(
                "tensa", "tensahelp", "tensareload", "tensamodules", "tpl", "psend", "tparse", "tinfo", "discord"
        );
        for (String section : config.getKeys(false)) {
            Map<String, Object> values = config.getSection(section);
            if (values == null || !booleanValue(values.get("enabled"), true)) {
                continue;
            }
            Object rawCommands = values.get("command");
            if (rawCommands == null) {
                continue;
            }
            for (String alias : String.valueOf(rawCommands).split("[,;\\s]+")) {
                String normalized = alias.startsWith("/") ? alias.substring(1) : alias;
                normalized = normalized.trim().toLowerCase(java.util.Locale.ROOT);
                if (normalized.isBlank()) {
                    continue;
                }
                if (reserved.contains(normalized) || !aliases.add(normalized)) {
                    throw new DiscordConfigurationException("chats.yml contains a duplicate or reserved command: " + normalized);
                }
            }
            validateMiniMessage(values.get("format"), "chats.yml " + section + ".format");
            validateMiniMessage(values.get("to_format"), "chats.yml " + section + ".to_format");
            validateMiniMessage(values.get("from_format"), "chats.yml " + section + ".from_format");
        }
    }

    private static void validateProxyChat(YamlAdapter config) {
        bounded(config.getInt("proxy_chat.max_length", 256), 1, 1_000, "proxy_chat.max_length");
        bounded(config.getLong("proxy_chat.cooldown_millis", 1_500), 0, 60_000, "proxy_chat.cooldown_millis");
        bounded(config.getLong("proxy_chat.duplicate_window_millis", 15_000), 0, 600_000, "proxy_chat.duplicate_window_millis");
        bounded(config.getInt("proxy_chat.max_repeated_characters", 4), 1, 100, "proxy_chat.max_repeated_characters");
        validateMiniMessage(config.getString("proxy_chat.format", ""), "discord.yml proxy_chat.format");
        validateMiniMessage(config.getString("proxy_chat.discord_format", ""), "discord.yml proxy_chat.discord_format");
        validateMiniMessage(config.getString("proxy_chat.cooldown_message", ""), "discord.yml proxy_chat.cooldown_message");
        validateMiniMessage(config.getString("proxy_chat.duplicate_message", ""), "discord.yml proxy_chat.duplicate_message");
    }

    private static void validateLinkStore(DiscordSettings settings) {
        AtomicLinkStore store = new AtomicLinkStore(Tensa.pluginPath, settings.linkStorePath());
        try {
            store.load();
        } catch (IOException exception) {
            throw new DiscordConfigurationException("Discord link store could not be validated safely");
        }
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        return fallback;
    }

    private static void validateMiniMessage(Object value, String key) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value);
        if (text.length() > 4_096) {
            throw new DiscordConfigurationException(key + " is longer than 4096 characters");
        }
        try {
            MiniMessage.miniMessage().deserialize(text);
        } catch (RuntimeException exception) {
            throw new DiscordConfigurationException(key + " contains invalid MiniMessage markup");
        }
    }

    private static void bounded(long value, long minimum, long maximum, String key) {
        if (value < minimum || value > maximum) {
            throw new DiscordConfigurationException(
                    "discord.yml " + key + " must be between " + minimum + " and " + maximum
            );
        }
    }

    private record Prepared(
            YamlAdapter chatConfig,
            YamlAdapter discordConfig,
            DiscordSettings discordSettings
    ) {
    }

    private static final class ActiveRuntime {
        private final ProxyChatService proxyChat;
        private final ChatCommands chatCommands;
        private DiscordRuntime discordRuntime;
        private VelocityBackendStatusMonitor statusMonitor;
        private VelocityDiscordBackendBridge backendEventBridge;
        private ChannelIdentifier backendEventChannel;
        private boolean discordCommandRegistered;

        private ActiveRuntime(ProxyChatService proxyChat, ChatCommands chatCommands) {
            this.proxyChat = proxyChat;
            this.chatCommands = chatCommands;
        }

        ProxyChatService proxyChat() {
            return proxyChat;
        }

        ChatCommands chatCommands() {
            return chatCommands;
        }
    }
}
