package ua.co.tensa.modules.authbridge.data;

import ua.co.tensa.config.model.ConfigBase;
import ua.co.tensa.config.model.ann.CfgKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AuthBridgeConfig extends ConfigBase {
    private static AuthBridgeConfig instance;

    @CfgKey(
            value = "secret_file",
            comment = "Relative file containing the dedicated Base64 bridge secret"
    )
    public String secretFile = "auth-bridge/secret.key";

    @CfgKey(
            value = "allow_from",
            comment = "Exact Velocity backend server names allowed to query authentication state"
    )
    public List<String> allowFrom = new ArrayList<>();

    @CfgKey(
            value = "source_bindings",
            comment = "Exact Velocity server name to signed backend ID mapping; aliases may share an ID and keys must match allow_from"
    )
    public Map<String, Object> sourceBindings = new LinkedHashMap<>();

    @CfgKey(
            value = "suppress_same_backend_alias_reconnect",
            comment = "Keep the current allowed Velocity alias when LibreLogin selects another alias bound to the same backend ID"
    )
    public boolean suppressSameBackendAliasReconnect = true;

    @CfgKey(value = "maximum_frame_ttl_seconds", comment = "Maximum lifetime of signed bridge frames")
    public int maximumFrameTtlSeconds = 15;

    @CfgKey(value = "maximum_clock_skew_seconds", comment = "Accepted clock skew for signed bridge frames")
    public int maximumClockSkewSeconds = 5;

    @CfgKey(value = "replay_capacity", comment = "Maximum live message, nonce and session replay entries")
    public int replayCapacity = 16_384;

    @CfgKey(
            value = "post_login_sync_delay_millis",
            comment = "Delay before reconciling LibreLogin state after Velocity PostLoginEvent"
    )
    public int postLoginSyncDelayMillis = 50;

    @CfgKey(
            value = "heartbeat_interval_seconds",
            comment = "Periodic LibreLogin reconciliation interval; must fit at least twice inside the authorization lease"
    )
    public int heartbeatIntervalSeconds = 10;

    @CfgKey(
            value = "authorization_lease_seconds",
            comment = "Backend authorization lease duration; supported range is 5 to 300 seconds"
    )
    public int authorizationLeaseSeconds = 30;

    @CfgKey(
            value = "log_transitions",
            comment = "Log successful authentication state transitions, excluding heartbeat refreshes"
    )
    public boolean logTransitions = false;

    private AuthBridgeConfig() {
        super("auth-bridge/config.yml");
    }

    public static synchronized AuthBridgeConfig get() {
        if (instance == null) {
            instance = new AuthBridgeConfig();
        }
        return instance;
    }
}
