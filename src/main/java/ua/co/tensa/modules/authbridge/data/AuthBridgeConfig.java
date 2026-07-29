package ua.co.tensa.modules.authbridge.data;

import ua.co.tensa.config.model.ConfigBase;
import ua.co.tensa.config.model.ann.CfgKey;

import java.util.ArrayList;
import java.util.List;

public final class AuthBridgeConfig extends ConfigBase {
    private static AuthBridgeConfig instance;

    @CfgKey(
            value = "secret_file",
            comment = "Relative file containing a dedicated bridge secret (plain text or base64:<value>)"
    )
    public String secretFile = "auth-bridge/secret.key";

    @CfgKey(
            value = "allow_from",
            comment = "Exact Velocity backend server names allowed to query authentication state"
    )
    public List<String> allowFrom = new ArrayList<>();

    @CfgKey(value = "message_ttl_seconds", comment = "Lifetime of signed bridge messages")
    public int messageTtlSeconds = 10;

    @CfgKey(value = "clock_skew_seconds", comment = "Accepted clock skew for signed bridge messages")
    public int clockSkewSeconds = 2;

    @CfgKey(
            value = "post_login_sync_delay_millis",
            comment = "Delay before reconciling LibreLogin state after Velocity PostLoginEvent"
    )
    public int postLoginSyncDelayMillis = 50;

    @CfgKey(value = "log_transitions", comment = "Log authentication state publications")
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
