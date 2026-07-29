package ua.co.tensa.modules.authbridge;

import ua.co.tensa.Tensa;
import ua.co.tensa.modules.AbstractModule;
import ua.co.tensa.modules.ModuleEntry;
import ua.co.tensa.modules.authbridge.data.AuthBridgeConfig;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public final class LibreLoginAuthBridgeModule {
    private static final ModuleEntry IMPL = new AbstractModule(
            "librelogin-auth-bridge",
            "LibreLogin Auth Bridge"
    ) {
        @Override
        protected void onEnable() {
            LibreLoginAuthBridgeModule.enableImpl(this);
        }

        @Override
        protected void onDisable() {
            LibreLoginAuthBridgeModule.disableImpl();
        }
    };

    public static final ModuleEntry ENTRY = IMPL;
    private static AuthBridgeRuntime runtime;

    private LibreLoginAuthBridgeModule() {
    }

    private static void enableImpl(AbstractModule owner) {
        if (Tensa.server.getPluginManager().getPlugin("librelogin").isEmpty()) {
            throw new IllegalStateException("LibreLogin is not installed; auth bridge remains disabled");
        }

        AuthBridgeConfig config = AuthBridgeConfig.get();
        config.reloadCfg();
        validateConfig(config);
        byte[] secret = AuthBridgeSecret.load(Tensa.pluginPath, config.secretFile);
        AuthenticationStateSource source = null;
        AuthBridgeRuntime created = null;
        try {
            source = LibreLoginAuthSource.open(Tensa.server);
            BridgeScheduler scheduler = new BridgeScheduler() {
                @Override
                public void execute(Runnable task) {
                    owner.schedule(task, 0L, TimeUnit.MILLISECONDS);
                }

                @Override
                public void delayed(Runnable task, long delayMillis) {
                    owner.schedule(task, delayMillis, TimeUnit.MILLISECONDS);
                }
            };
            created = new AuthBridgeRuntime(
                    Tensa.server,
                    source,
                    scheduler,
                    config.allowFrom,
                    secret,
                    Clock.systemUTC(),
                    Duration.ofSeconds(config.messageTtlSeconds),
                    Duration.ofSeconds(config.clockSkewSeconds),
                    config.postLoginSyncDelayMillis,
                    config.logTransitions
            );
            java.util.Arrays.fill(secret, (byte) 0);
            runtime = created;
            owner.registerListener(created);
            created.start();
        } catch (Throwable throwable) {
            if (created != null) {
                created.close();
            } else if (source != null) {
                source.close();
            }
            java.util.Arrays.fill(secret, (byte) 0);
            runtime = null;
            throw throwable;
        }
    }

    private static void disableImpl() {
        AuthBridgeRuntime current = runtime;
        runtime = null;
        if (current != null) {
            current.close();
        }
    }

    private static void validateConfig(AuthBridgeConfig config) {
        if (config.messageTtlSeconds <= 0 || config.messageTtlSeconds > 300) {
            throw new IllegalStateException("message_ttl_seconds must be between 1 and 300");
        }
        if (config.clockSkewSeconds < 0 || config.clockSkewSeconds > 60) {
            throw new IllegalStateException("clock_skew_seconds must be between 0 and 60");
        }
        if (config.postLoginSyncDelayMillis < 0 || config.postLoginSyncDelayMillis > 5_000) {
            throw new IllegalStateException("post_login_sync_delay_millis must be between 0 and 5000");
        }
    }
}
