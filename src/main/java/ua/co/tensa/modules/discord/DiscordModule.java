package ua.co.tensa.modules.discord;

import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import ua.co.tensa.Tensa;
import ua.co.tensa.modules.AbstractModule;
import ua.co.tensa.modules.ModuleEntry;
import ua.co.tensa.modules.chat.ChatModule;
import ua.co.tensa.modules.discord.data.DiscordConfig;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public final class DiscordModule extends AbstractModule {
    private static final DiscordModule INSTANCE = new DiscordModule();
    public static final ModuleEntry ENTRY = INSTANCE;

    private volatile DiscordRuntime runtime;
    private AutoCloseable chatSink;
    private VelocityBackendStatusMonitor statusMonitor;
    private VelocityDiscordBackendBridge backendEventBridge;
    private ChannelIdentifier backendEventChannel;

    private DiscordModule() {
        super("discord", "Discord");
    }

    @Override
    protected void onEnable() {
        DiscordConfig config = new DiscordConfig();
        config.reloadCfg();
        DiscordSettings settings = config.settings(System.getenv());

        AtomicLinkStore store = new AtomicLinkStore(Tensa.pluginPath, settings.linkStorePath());
        try {
            store.load();
        } catch (IOException e) {
            throw new IllegalStateException("Discord link store could not be loaded safely");
        }

        JdaDiscordGateway gateway = new JdaDiscordGateway(settings);
        DiscordLinkService linkService = new DiscordLinkService(
                store,
                new LinkCodeRegistry(settings.linkCodeTtl(), settings.linkCodeLength()),
                gateway,
                settings
        );
        DiscordRuntime next = new DiscordRuntime(
                settings,
                gateway,
                linkService,
                new DiscordDelivery(gateway, new DiscordWebhookClient(settings))
        );
        runtime = next;
        next.start();
        chatSink = ChatModule.registerOutboundSink(next::relayMinecraftChat);
        DiscordVelocityListener velocityListener = registerListener(new DiscordVelocityListener(next, settings));
        Tensa.server.getAllPlayers().forEach(velocityListener::prime);

        if (settings.backendStatusMessages()) {
            statusMonitor = new VelocityBackendStatusMonitor(Tensa.server, settings, next);
            scheduleRepeating(
                    statusMonitor::poll,
                    1,
                    settings.backendStatusPollInterval().toSeconds(),
                    TimeUnit.SECONDS
            );
        }
        if (settings.advancementMessages()) {
            VelocityDiscordBackendBridge bridge = new VelocityDiscordBackendBridge(settings, next);
            backendEventBridge = bridge;
            backendEventChannel = bridge.channel();
            Tensa.server.getChannelRegistrar().register(backendEventChannel);
            registerListener(bridge);
        }
        registerCommand("discord", "", new DiscordCommand(next));
    }

    @Override
    protected void onDisable() {
        unregisterCommands("discord");
        VelocityBackendStatusMonitor monitor = statusMonitor;
        statusMonitor = null;
        if (monitor != null) {
            monitor.close();
        }
        VelocityDiscordBackendBridge bridge = backendEventBridge;
        backendEventBridge = null;
        if (bridge != null) {
            bridge.close();
        }
        ChannelIdentifier channel = backendEventChannel;
        backendEventChannel = null;
        if (channel != null && Tensa.server != null) {
            try {
                Tensa.server.getChannelRegistrar().unregister(channel);
            } catch (RuntimeException ignored) {
            }
        }
        AutoCloseable sink = chatSink;
        chatSink = null;
        if (sink != null) {
            try {
                sink.close();
            } catch (Exception ignored) {
            }
        }
        DiscordRuntime current = runtime;
        runtime = null;
        if (current != null) {
            current.close();
        }
    }
}
