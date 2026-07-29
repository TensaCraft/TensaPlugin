package ua.co.tensa.modules.authbridge;

import ua.co.tensa.Tensa;
import ua.co.tensa.modules.AbstractModule;
import ua.co.tensa.modules.ModuleEntry;
import ua.co.tensa.modules.authbridge.data.AuthBridgeConfig;
import ua.co.tensa.authbridge.protocol.security.AuthSecurityPolicy;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
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

                @Override
                public void repeating(Runnable task, long initialDelayMillis, long intervalMillis) {
                    owner.scheduleRepeating(
                            task,
                            initialDelayMillis,
                            intervalMillis,
                            TimeUnit.MILLISECONDS
                    );
                }
            };
            created = new AuthBridgeRuntime(
                    Tensa.server,
                    source,
                    scheduler,
                    config.allowFrom,
                    sourceBindings(config.sourceBindings),
                    secret,
                    Clock.systemUTC(),
                    new SecureRandom(),
                    new AuthSecurityPolicy(
                            Duration.ofSeconds(config.maximumClockSkewSeconds),
                            Duration.ofSeconds(config.maximumFrameTtlSeconds),
                            config.replayCapacity
                    ),
                    config.postLoginSyncDelayMillis,
                    Duration.ofSeconds(config.heartbeatIntervalSeconds),
                    Duration.ofSeconds(config.authorizationLeaseSeconds),
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
        if (config.maximumFrameTtlSeconds <= 0 || config.maximumFrameTtlSeconds > 120) {
            throw new IllegalStateException("maximum_frame_ttl_seconds must be between 1 and 120");
        }
        if (config.maximumClockSkewSeconds <= 0 || config.maximumClockSkewSeconds > 60) {
            throw new IllegalStateException("maximum_clock_skew_seconds must be between 1 and 60");
        }
        if (config.replayCapacity < 128 || config.replayCapacity > 1_000_000) {
            throw new IllegalStateException("replay_capacity must be between 128 and 1000000");
        }
        if (config.postLoginSyncDelayMillis < 0 || config.postLoginSyncDelayMillis > 5_000) {
            throw new IllegalStateException("post_login_sync_delay_millis must be between 0 and 5000");
        }
        validateAuthorizationTiming(
                config.heartbeatIntervalSeconds,
                config.authorizationLeaseSeconds
        );
    }

    static void validateAuthorizationTiming(
            int heartbeatIntervalSeconds,
            int authorizationLeaseSeconds
    ) {
        if (authorizationLeaseSeconds < 5 || authorizationLeaseSeconds > 300) {
            throw new IllegalStateException(
                    "authorization_lease_seconds must be between 5 and 300"
            );
        }
        if (heartbeatIntervalSeconds <= 0) {
            throw new IllegalStateException("heartbeat_interval_seconds must be greater than 0");
        }
        if ((long) heartbeatIntervalSeconds * 2L > authorizationLeaseSeconds) {
            throw new IllegalStateException(
                    "heartbeat_interval_seconds must fit at least twice inside "
                            + "authorization_lease_seconds"
            );
        }
    }

    private static Map<String, String> sourceBindings(Map<String, Object> configured) {
        Map<String, String> bindings = new LinkedHashMap<>();
        if (configured != null) {
            configured.forEach((server, backendId) -> {
                if (!(backendId instanceof String value)) {
                    throw new IllegalStateException(
                            "source_bindings." + server + " must be a backend ID string"
                    );
                }
                bindings.put(server, value);
            });
        }
        return Map.copyOf(bindings);
    }
}
