package ua.co.tensa.modules.bridge;

import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import ua.co.tensa.Tensa;
import ua.co.tensa.modules.AbstractModule;
import ua.co.tensa.modules.ModuleEntry;
import ua.co.tensa.modules.bridge.data.BridgeConfig;

import java.util.List;

public final class ProxyBridgeModule {
    private static final ModuleEntry IMPL = new AbstractModule("proxy-bridge", "ProxyBridge") {
        private ChannelIdentifier channel;
        private LegacyProxyBridgeRuntime runtime;

        @Override
        protected void onEnable() {
            BridgeConfig config = BridgeConfig.get();
            config.reloadCfg();

            LegacyProxyBridgeRuntime created = new LegacyProxyBridgeRuntime(
                    config.compatibilityMode,
                    config.channel,
                    config.token,
                    config.allowFrom,
                    config.log,
                    ua.co.tensa.Util::executeCommand
            );
            runtime = created;
            Tensa.server.getChannelRegistrar().register(created.channel());
            channel = created.channel();
            registerListener(created);
            AbstractModule.registerCommand(
                    "tproxydebug",
                    "tpbdebug",
                    new ProxyBridgeDebugCommand()
            );
        }

        @Override
        protected void onDisable() {
            LegacyProxyBridgeRuntime current = runtime;
            runtime = null;
            if (current != null) {
                current.close();
            }
            if (channel != null) {
                try {
                    Tensa.server.getChannelRegistrar().unregister(channel);
                } catch (Throwable ignored) {
                }
                channel = null;
            }
            AbstractModule.unregisterCommands("tproxydebug", "tpbdebug");
        }
    };

    public static final ModuleEntry ENTRY = IMPL;

    private ProxyBridgeModule() {
    }

    static Status status() {
        BridgeConfig config = BridgeConfig.get();
        return new Status(
                IMPL.isEnabled(),
                config.compatibilityMode,
                config.channel,
                config.token != null && !config.token.isBlank(),
                config.allowFrom == null ? List.of() : List.copyOf(config.allowFrom),
                config.log
        );
    }

    public static void enable() {
        IMPL.enable();
    }

    public static void disable() {
        IMPL.disable();
    }

    record Status(
            boolean enabled,
            boolean compatibilityMode,
            String channel,
            boolean tokenConfigured,
            List<String> allowFrom,
            boolean log
    ) {
    }
}
