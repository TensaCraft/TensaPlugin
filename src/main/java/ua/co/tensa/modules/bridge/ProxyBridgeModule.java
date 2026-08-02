package ua.co.tensa.modules.bridge;

import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import ua.co.tensa.Tensa;
import ua.co.tensa.config.model.YamlConfigPreflight;
import ua.co.tensa.modules.AbstractModule;
import ua.co.tensa.modules.ModuleEntry;
import ua.co.tensa.modules.bridge.data.BridgeConfig;
import ua.co.tensa.modules.runtime.AtomicRuntimeSlot;

import java.util.List;

public final class ProxyBridgeModule {
    private static final BridgeModuleEntry IMPL = new BridgeModuleEntry();

    public static final ModuleEntry ENTRY = IMPL;

    private ProxyBridgeModule() {
    }

    static Status status() {
        Plan plan = IMPL.runtime.plan();
        return new Status(
                IMPL.isEnabled(),
                plan != null && plan.compatibilityMode(),
                plan == null ? "" : plan.channel(),
                plan != null && plan.token() != null && !plan.token().isBlank(),
                plan == null ? List.of() : plan.allowFrom(),
                plan != null && plan.log()
        );
    }

    public static void enable() {
        IMPL.enable();
    }

    public static void disable() {
        IMPL.disable();
    }

    private static Plan prepare() {
        YamlConfigPreflight.validate(Tensa.pluginPath.resolve("bridge.yml"));
        BridgeConfig config = BridgeConfig.get();
        config.reloadCfg();
        Plan plan = new Plan(
                config.compatibilityMode,
                config.channel,
                config.token,
                config.allowFrom == null ? List.of() : List.copyOf(config.allowFrom),
                config.log
        );
        try (LegacyProxyBridgeRuntime ignored = createRuntime(plan)) {
            return plan;
        }
    }

    private static ActiveRuntime activate(BridgeModuleEntry owner, Plan plan) {
        LegacyProxyBridgeRuntime runtime = createRuntime(plan);
        ChannelIdentifier channel = runtime.channel();
        boolean channelRegistered = false;
        boolean listenerRegistered = false;
        try {
            Tensa.server.getChannelRegistrar().register(channel);
            channelRegistered = true;
            owner.registerListener(runtime);
            listenerRegistered = true;
            AbstractModule.registerCommand("tproxydebug", "tpbdebug", new ProxyBridgeDebugCommand());
            return new ActiveRuntime(runtime, channel);
        } catch (RuntimeException failure) {
            if (listenerRegistered) owner.unregisterListener(runtime);
            if (channelRegistered) {
                try { Tensa.server.getChannelRegistrar().unregister(channel); } catch (RuntimeException ignored) { }
            }
            runtime.close();
            AbstractModule.unregisterCommands("tproxydebug", "tpbdebug");
            throw failure;
        }
    }

    private static void deactivate(BridgeModuleEntry owner, ActiveRuntime active) {
        if (active == null) return;
        AbstractModule.unregisterCommands("tproxydebug", "tpbdebug");
        owner.unregisterListener(active.runtime());
        try { Tensa.server.getChannelRegistrar().unregister(active.channel()); } catch (RuntimeException ignored) { }
        active.runtime().close();
    }

    private static LegacyProxyBridgeRuntime createRuntime(Plan plan) {
        return new LegacyProxyBridgeRuntime(
                plan.compatibilityMode(),
                plan.channel(),
                plan.token(),
                plan.allowFrom(),
                plan.log(),
                ua.co.tensa.Util::executeCommand
        );
    }

    private static final class BridgeModuleEntry extends AbstractModule {
        private final AtomicRuntimeSlot<Plan, ActiveRuntime> runtime =
                new AtomicRuntimeSlot<>(plan -> activate(this, plan), active -> deactivate(this, active));

        private BridgeModuleEntry() {
            super("proxy-bridge", "ProxyBridge");
        }

        @Override protected void onEnable() { runtime.start(prepare()); }
        @Override protected void onDisable() { runtime.close(); }
        @Override protected void onReload() { runtime.replace(prepare()); }
        @Override protected boolean restartOnReloadFailure() { return false; }
    }

    private record Plan(boolean compatibilityMode, String channel, String token, List<String> allowFrom, boolean log) {
    }

    private record ActiveRuntime(LegacyProxyBridgeRuntime runtime, ChannelIdentifier channel) {
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
