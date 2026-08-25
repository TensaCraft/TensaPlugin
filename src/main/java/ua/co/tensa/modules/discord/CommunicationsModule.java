package ua.co.tensa.modules.discord;

import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import ua.co.tensa.config.model.YamlAdapter;
import ua.co.tensa.config.model.YamlFileIO;
import ua.co.tensa.modules.AbstractModule;
import ua.co.tensa.modules.ModuleEntry;
import ua.co.tensa.modules.runtime.AtomicRuntimeSlot;
import ua.co.tensa.modules.runtime.ModuleScheduler;
import ua.co.tensa.modules.runtime.RuntimeSchedulerFactory;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.time.Duration;

/** One lifecycle owner for Minecraft chat, Discord linking, and both relay directions. */
public final class CommunicationsModule extends AbstractModule {
    private static final CommunicationsModule INSTANCE = new CommunicationsModule();
    public static final ModuleEntry ENTRY = INSTANCE;

    private final AtomicRuntimeSlot<Prepared, ActiveRuntime> runtimeSlot =
            new AtomicRuntimeSlot<>(this::activate, this::deactivate);
    private final Map<UUID, UUID> privateReplyTargets = new ConcurrentHashMap<>();
    private final CommunicationsMetrics metrics = new CommunicationsMetrics();

    private CommunicationsModule() {
        super("communications", "Communications");
    }

    @Override
    protected void onEnable() {
        runtimeSlot.start(prepare(true));
    }

    @Override
    protected void onDisable() {
        runtimeSlot.close();
        privateReplyTargets.clear();
    }

    @Override
    protected void onReload() {
        // Preparation performs parsing and all semantic validation while the
        // current runtime is still fully operational.
        runtimeSlot.replace(prepare(false));
    }

    @Override
    protected boolean restartOnReloadFailure() {
        return false;
    }

    private Prepared prepare(boolean allowDegraded) {
        validateYaml(Tensa.pluginPath.resolve(CommunicationsConfigBootstrap.CHATS_FILE));
        validateYaml(Tensa.pluginPath.resolve(CommunicationsConfigBootstrap.DISCORD_FILE));

        ChatConfig chatConfig = new ChatConfig();
        chatConfig.reloadCfg();
        DiscordConfig discordConfig = new DiscordConfig();
        discordConfig.reloadCfg();

        validateChat(chatConfig.adapter());
        validateProxyChat(discordConfig.adapter());
        DiscordSettings discordSettings = discordConfig.enabled
                ? discordConfig.settings(System.getenv())
                : null;
        return new Prepared(
                chatConfig.adapter(), discordConfig.adapter(), chatConfig.enabled, discordSettings, allowDegraded);
    }

    private ActiveRuntime activate(Prepared plan) {
        AtomicReference<DiscordRuntime> discordReference = new AtomicReference<>();
        ProxyChatService proxyChat = new ProxyChatService(
                plan.chatConfig(),
                plan.discordConfig(),
                message -> {
                    DiscordRuntime discord = discordReference.get();
                    return discord == null
                            ? ua.co.tensa.modules.chat.ProxyChatRelay.Result.DISABLED
                            : discord.relayMinecraftChat(message);
                }
        );
        ChatCommands chatCommands = new ChatCommands(plan.chatConfig(), proxyChat, privateReplyTargets);
        ActiveRuntime active = new ActiveRuntime(
                proxyChat, chatCommands, RuntimeSchedulerFactory.create("tensa-communications"));

        try {
            if (plan.discordSettings() != null) {
                try {
                    startDiscord(active, plan.discordSettings(), discordReference);
                } catch (RuntimeException operationalFailure) {
                    if (!plan.allowDegraded()) {
                        throw operationalFailure;
                    }
                    metrics.recordFailure(operationalFailure);
                    Message.warn("communications transition=degraded component=discord failure="
                            + DiscordDiagnostics.unwrap(operationalFailure).getClass().getSimpleName());
                    scheduleDiscordRecovery(active, plan.discordSettings(), discordReference);
                }
            }
            if (plan.chatEnabled()) {
                registerListener(new ProxyChatListener(proxyChat, chatCommands));
                chatCommands.register();
            }
            active.scheduler.schedule(ModuleScheduler.job("communications-resource-telemetry", () -> updateResources(active, true))
                    .scope("communications-telemetry")
                    .dedupe("communications-resource-telemetry")
                    .delay(Duration.ofMinutes(1))
                    .interval(Duration.ofMinutes(1))
                    .timeout(Duration.ofSeconds(5))
                    .onDeadLetter(metrics::recordFailure)
                    .build());
            updateResources(active, false);
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
        DiscordLinkRepository store = new JdbcDiscordLinkRepository(Tensa.storage, settings.maxLinks());
        try {
            store.initialize();
        } catch (IOException exception) {
            throw new IllegalStateException("Discord link repository could not be initialized safely", exception);
        }

        JdaDiscordGateway gateway = new JdaDiscordGateway(settings, active.scheduler);
        DiscordLinkService linkService = new DiscordLinkService(
                store,
                new LinkCodeRegistry(
                        settings.linkCodeTtl(), settings.linkCodeLength(), settings.eventStateCapacity()),
                gateway,
                settings
        );
        DiscordWebhookClient webhookClient = new DiscordWebhookClient(settings);
        DiscordWebhookBindingRepository webhookBindings = new DiscordWebhookBindingRepository(Tensa.storage);
        webhookBindings.initialize();
        DiscordWebhookProvisioner webhookProvisioner = new DiscordWebhookProvisioner(
                settings, gateway, webhookClient, webhookBindings, active.scheduler, metrics);
        DiscordRuntime discord = new DiscordRuntime(
                settings,
                gateway,
                linkService,
                new DiscordDelivery(gateway, webhookClient),
                active.proxyChat()::publishExternal,
                metrics,
                webhookProvisioner,
                active.scheduler
        );
        active.discordRuntime = discord;
        discordReference.set(discord);
        try {
            discord.start();
        } catch (RuntimeException failure) {
            active.discordRuntime = null;
            discordReference.compareAndSet(discord, null);
            discord.close();
            throw failure;
        }

        DiscordVelocityListener velocityListener = registerListener(new DiscordVelocityListener(discord, settings));
        Tensa.server.getAllPlayers().forEach(velocityListener::prime);

        if (settings.backendStatusMessages()) {
            active.statusMonitor = new VelocityBackendStatusMonitor(Tensa.server, settings, discord);
            active.scheduler.schedule(ModuleScheduler.job("communications-backend-status", active.statusMonitor::poll)
                    .scope("communications-status")
                    .dedupe("communications-backend-status")
                    .delay(Duration.ofSeconds(1))
                    .interval(settings.backendStatusPollInterval())
                    .timeout(settings.backendStatusPingTimeout().plusSeconds(1))
                    .onDeadLetter(metrics::recordFailure)
                    .build());
        }
        if (settings.advancementMessages() || settings.deathMessages()) {
            VelocityDiscordBackendBridge bridge = new VelocityDiscordBackendBridge(settings, discord);
            active.backendEventBridge = bridge;
            active.backendEventChannel = bridge.channel();
            Tensa.server.getChannelRegistrar().register(active.backendEventChannel);
            registerListener(bridge);
        }
        registerCommand("discord", "", new DiscordCommand(discord));
        active.discordCommandRegistered = true;
        active.scheduler.schedule(ModuleScheduler.job("communications-link-code-cleanup", discord::cleanupExpiredLinkCodes)
                .scope("communications-linking")
                .dedupe("communications-link-code-cleanup")
                .delay(Duration.ofMinutes(1))
                .interval(Duration.ofMinutes(1))
                .timeout(Duration.ofSeconds(5))
                .onDeadLetter(metrics::recordFailure)
                .build());
    }

    private void deactivate(ActiveRuntime active) {
        if (active == null) {
            return;
        }
        active.closed = true;
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
        active.scheduler.close();
        active.proxyChat().clear();
    }

    private void scheduleDiscordRecovery(
            ActiveRuntime active,
            DiscordSettings settings,
            AtomicReference<DiscordRuntime> discordReference
    ) {
        long interval = Math.max(5L, Math.min(30L, settings.reconnectMaxDelaySeconds()));
        active.discordReconnectTask = active.scheduler.schedule(ModuleScheduler.job("communications-discord-recovery", () -> {
            synchronized (active) {
                if (active.closed || active.discordRuntime != null) {
                    return;
                }
                metrics.reconnectAttempted();
                try {
                    startDiscord(active, settings, discordReference);
                    active.discordReconnectTask.cancel();
                    active.discordReconnectTask = null;
                    Message.info("communications transition=recovered component=discord");
                } catch (RuntimeException failure) {
                    metrics.recordFailure(failure);
                }
            }
        })
                .scope("communications-recovery")
                .dedupe("communications-discord-recovery")
                .delay(Duration.ofSeconds(interval))
                .interval(Duration.ofSeconds(interval))
                .attempts(1)
                .timeout(Duration.ofSeconds(Math.max(10L, interval)))
                .onDeadLetter(metrics::recordFailure)
                .build());
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

    static void validateChat(YamlAdapter config) {
        Set<String> aliases = new HashSet<>();
        Set<String> reserved = Set.of(
                "tensa", "tpl", "psend", "tparse",
                "discord"
        );
        for (String section : config.getKeys(false)) {
            Map<String, Object> values = config.getSection(section);
            if (values == null) {
                continue;
            }
            validateOptionalBoolean(values, section, "enabled");
            validateOptionalBoolean(values, section, "native");
            validateOptionalBoolean(values, section, "see_all");
            validateOptionalString(values, section, "type");
            validateOptionalString(values, section, "permission");
            validateOptionalString(values, section, "command");
            validateCommandList(values, section);
            validateMiniMessage(values.get("format"), "chats.yml " + section + ".format");
            validateMiniMessage(values.get("to_format"), "chats.yml " + section + ".to_format");
            validateMiniMessage(values.get("from_format"), "chats.yml " + section + ".from_format");
            if (!booleanValue(values.get("enabled"), true)) {
                continue;
            }

            Object rawCommands = values.get("command");
            java.util.ArrayList<String> configuredCommands = new java.util.ArrayList<>();
            if (rawCommands instanceof String text) {
                configuredCommands.addAll(java.util.List.of(text.split("[,;\\s]+")));
            }
            if (values.get("commands") instanceof Iterable<?> commandList) {
                commandList.forEach(value -> configuredCommands.add((String) value));
            }
            for (String alias : configuredCommands) {
                String normalized = alias.startsWith("/") ? alias.substring(1) : alias;
                normalized = normalized.trim().toLowerCase(java.util.Locale.ROOT);
                if (normalized.isBlank()) {
                    continue;
                }
                if (reserved.contains(normalized) || !aliases.add(normalized)) {
                    throw new DiscordConfigurationException("chats.yml contains a duplicate or reserved command: " + normalized);
                }
            }
        }
    }

    private static void validateOptionalString(Map<String, Object> section, String sectionName, String key) {
        Object value = section.get(key);
        if (value != null && !(value instanceof String)) {
            throw new DiscordConfigurationException("chats.yml " + sectionName + "." + key + " must be a string");
        }
    }

    private static void validateOptionalBoolean(Map<String, Object> section, String sectionName, String key) {
        Object value = section.get(key);
        if (value != null && !(value instanceof Boolean)) {
            throw new DiscordConfigurationException("chats.yml " + sectionName + "." + key + " must be true or false");
        }
    }

    private static void validateCommandList(Map<String, Object> section, String sectionName) {
        Object value = section.get("commands");
        if (value == null) {
            return;
        }
        if (!(value instanceof Iterable<?> commands)) {
            throw new DiscordConfigurationException("chats.yml " + sectionName + ".commands must be a string list");
        }
        for (Object command : commands) {
            if (!(command instanceof String)) {
                throw new DiscordConfigurationException("chats.yml " + sectionName + ".commands must contain only strings");
            }
        }
    }

    static void validateProxyChat(YamlAdapter config) {
        Map<String, Object> proxyChat = config.getSection("proxy_chat");
        if (proxyChat == null) {
            return;
        }
        Object enabled = proxyChat.get("enabled");
        if (enabled != null && !(enabled instanceof Boolean)) {
            throw new DiscordConfigurationException("discord.yml proxy_chat.enabled must be true or false");
        }
        Object excluded = proxyChat.get("excluded_servers");
        if (excluded != null) {
            if (!(excluded instanceof Iterable<?> servers)) {
                throw new DiscordConfigurationException("discord.yml proxy_chat.excluded_servers must be a string list");
            }
            for (Object server : servers) {
                if (!(server instanceof String)) {
                    throw new DiscordConfigurationException(
                            "discord.yml proxy_chat.excluded_servers must contain only strings");
                }
            }
        }
        Object aliases = proxyChat.get("server_aliases");
        if (aliases != null) {
            if (!(aliases instanceof Map<?, ?> values)) {
                throw new DiscordConfigurationException("discord.yml proxy_chat.server_aliases must be a string map");
            }
            for (Map.Entry<?, ?> entry : values.entrySet()) {
                if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof String)) {
                    throw new DiscordConfigurationException(
                            "discord.yml proxy_chat.server_aliases must map backend names to strings");
                }
            }
        }
        validateMiniMessage(proxyChat.get("discord_format"), "discord.yml proxy_chat.discord_format");

        Map<String, Object> minecraftRelay = config.getSection("relay.minecraft_to_discord");
        if (minecraftRelay == null || minecraftRelay.get("channels") == null) {
            return;
        }
        Object channels = minecraftRelay.get("channels");
        if (!(channels instanceof Iterable<?> values)) {
            throw new DiscordConfigurationException(
                    "discord.yml relay.minecraft_to_discord.channels must be a string list");
        }
        for (Object channel : values) {
            if (!(channel instanceof String text)) {
                throw new DiscordConfigurationException(
                        "discord.yml relay.minecraft_to_discord.channels must contain only strings");
            }
            if (!text.trim().matches("[a-zA-Z0-9_.-]{1,64}")) {
                throw new DiscordConfigurationException(
                        "discord.yml relay.minecraft_to_discord.channels contains an invalid logical channel");
            }
        }
    }

    public static CommunicationsMetrics.Snapshot diagnosticsSnapshot() {
        ActiveRuntime active = INSTANCE.runtimeSlot.runtime();
        if (active != null) {
            INSTANCE.updateResources(active, false);
        }
        DiscordRuntime discord = active == null ? null : active.discordRuntime;
        String backend = Tensa.storage == null ? "unavailable" : Tensa.storage.backendType();
        if (discord == null) {
            return INSTANCE.metrics.snapshot(
                    active == null ? "stopped" : "chat-only",
                    "unavailable",
                    "unavailable",
                    "unavailable",
                    backend,
                    0, 0, 0, 0
            );
        }
        return discord.diagnostics(backend);
    }

    private void updateResources(ActiveRuntime active, boolean logTransition) {
        if (active == null || active.closed) {
            return;
        }
        DiscordRuntime discord = active.discordRuntime;
        CommunicationsResourceSnapshot snapshot = active.resourceMonitor.capture(
                active.proxyChat().stateSize(),
                privateReplyTargets.size(),
                active.proxyChat().clickableUrlsRendered(),
                discord == null ? DiscordRuntime.ResourceState.empty() : discord.resourceState(),
                active.scheduler.snapshot()
        );
        metrics.observeResources(snapshot);
        if (!logTransition) {
            return;
        }
        CommunicationsResourceMonitor.Transition transition = active.resourceMonitor.evaluate(snapshot);
        if (transition == CommunicationsResourceMonitor.Transition.HIGH) {
            Message.warn("communications component=resources transition=heap-pressure heap_percent="
                    + snapshot.heapPercent() + " scheduler_queue=" + snapshot.schedulerQueueDepth());
        } else if (transition == CommunicationsResourceMonitor.Transition.RECOVERED) {
            Message.info("communications component=resources transition=recovered heap_percent="
                    + snapshot.heapPercent());
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
        if (!(value instanceof String text)) {
            throw new DiscordConfigurationException(key + " must be a MiniMessage string");
        }
        if (text.length() > 4_096) {
            throw new DiscordConfigurationException(key + " is longer than 4096 characters");
        }
        try {
            Message.convert(text);
        } catch (RuntimeException exception) {
            throw new DiscordConfigurationException(key + " contains invalid MiniMessage markup");
        }
    }

    private record Prepared(
            YamlAdapter chatConfig,
            YamlAdapter discordConfig,
            boolean chatEnabled,
            DiscordSettings discordSettings,
            boolean allowDegraded
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
        private ModuleScheduler.Handle discordReconnectTask;
        private final ModuleScheduler scheduler;
        private final CommunicationsResourceMonitor resourceMonitor;
        private boolean closed;

        private ActiveRuntime(ProxyChatService proxyChat, ChatCommands chatCommands, ModuleScheduler scheduler) {
            this.proxyChat = proxyChat;
            this.chatCommands = chatCommands;
            this.scheduler = scheduler;
            Runtime runtime = Runtime.getRuntime();
            this.resourceMonitor = new CommunicationsResourceMonitor(
                    runtime.totalMemory() - runtime.freeMemory(), 85, 75);
        }

        ProxyChatService proxyChat() {
            return proxyChat;
        }

        ChatCommands chatCommands() {
            return chatCommands;
        }
    }
}
