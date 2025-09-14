package ua.co.tensa.modules.bridge;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import ua.co.tensa.Tensa;
import ua.co.tensa.Util;
import ua.co.tensa.config.data.BridgeYAML;
import ua.co.tensa.modules.AbstractModule;
import ua.co.tensa.modules.ModuleEntry;

import java.nio.charset.StandardCharsets;

public class PMBridgeModule {
    private static final ModuleEntry IMPL = new AbstractModule("pm-bridge", "PluginMessage Bridge") {
        private ChannelIdentifier id;

        @Override protected void onEnable() {
            var cfg = BridgeYAML.getInstance().getReloadedFile();
            String ch = cfg.getString("channel", "tensa:exec");
            id = MinecraftChannelIdentifier.from(ch);
            Tensa.server.getChannelRegistrar().register(id);
            registerListener(new PMBridgeModule());
            ua.co.tensa.Message.info("PM-Bridge enabled on channel: " + ch);
        }

        @Override protected void onDisable() {
            if (id != null) {
                try { Tensa.server.getChannelRegistrar().unregister(id); } catch (Throwable ignored) {}
            }
        }
    };

    public static final ModuleEntry ENTRY = IMPL;
    public static void enable() { IMPL.enable(); }
    public static void disable() { IMPL.disable(); }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        // Always read the latest config when processing messages
        // Use current in-memory view; config is reloaded on module reload (/tensareload)
        var adapter = BridgeYAML.getInstance().adapter();
        String ch = adapter.getString("channel", "tensa:exec");
        ChannelIdentifier id = MinecraftChannelIdentifier.from(ch);
        // Only proceed for the configured channel; no extra spam logs
        if (!event.getIdentifier().equals(id)) return;

        // Only accept from backend servers
        if (!(event.getSource() instanceof ServerConnection serverConn)) return;

        String token = resolveToken(BridgeYAML.getInstance().getConfig());
        boolean log = adapter.getBoolean("log", true);
        java.util.List<String> allow = adapter.getStringList("allow_from");

        String serverName = serverConn.getServerInfo().getName();
        if (allow != null && !allow.isEmpty() && !isAllowedServer(allow, serverName)) {
            if (log) ua.co.tensa.Message.warn("PM-Bridge: blocked message from disallowed server '" + serverName + "'");
            return;
        }
        if (log) ua.co.tensa.Message.info("PM-Bridge: message on " + ch + " from server '" + serverName + "'");
        // No allowlist filtering: accept from any backend server

        String payload = new String(event.getData(), StandardCharsets.UTF_8);
        int idx = payload.indexOf(':');
        if (idx <= 0) {
            if (log) ua.co.tensa.Message.warn("PM-Bridge: invalid payload format");
            return;
        }
        String provided = payload.substring(0, idx);
        String cmd = payload.substring(idx + 1).trim();
        if (!provided.equals(token)) {
            if (log) ua.co.tensa.Message.warn("PM-Bridge: invalid token from " + serverName);
            return;
        }

        if (cmd.isEmpty()) return;
        if (log) ua.co.tensa.Message.info("PM-Bridge exec from " + serverName + ": /" + cmd);
        Util.executeCommand(cmd);
        event.setResult(PluginMessageEvent.ForwardResult.handled());
    }

    // Prevents config error spam: log at most once per 15s
    private static volatile long _lastCfgErr = 0L;
    private static void throttleConfigError(String msg) {
        long now = System.currentTimeMillis();
        if ((now - _lastCfgErr) > 15000) {
            ua.co.tensa.Message.error(msg);
            _lastCfgErr = now;
        }
    }

    // Allowlist helper for unit testing
    static boolean isAllowedServer(java.util.List<String> allow, String server) {
        if (allow == null || allow.isEmpty()) return true;
        if (server == null) return false;
        for (String a : allow) {
            if (server.equalsIgnoreCase(a)) return true;
        }
        return false;
    }

    public static String resolveToken(org.simpleyaml.configuration.file.YamlConfiguration cfg) {
        boolean useVelocitySecret = getBool(cfg, "use_velocity_secret", true);
        String token = cfg.getString("token", "");
        if (!useVelocitySecret) return token;
        try {
            java.nio.file.Path pluginsDir = ua.co.tensa.Tensa.pluginPath; // .../plugins/TENSA (may be relative)
            java.nio.file.Path absPlugins = pluginsDir == null ? null : pluginsDir.toAbsolutePath().normalize();
            java.nio.file.Path root = null;
            if (absPlugins != null) {
                root = absPlugins.getParent() != null ? absPlugins.getParent().getParent() : null; // go up: .../plugins/TENSA -> .../
            }
            if (root == null) {
                root = java.nio.file.Paths.get(".").toAbsolutePath().normalize();
            }
            // Direct default fallback: forwarding.secret in root
            java.nio.file.Path directSecret = root.resolve("forwarding.secret");
            if (java.nio.file.Files.exists(directSecret)) {
                String sec = java.nio.file.Files.readString(directSecret).trim();
                if (!sec.isEmpty()) return sec;
            }

            java.nio.file.Path velocityToml = root.resolve("velocity.toml");
            if (!java.nio.file.Files.exists(velocityToml)) return token;
            java.util.List<String> lines = java.nio.file.Files.readAllLines(velocityToml);
            String secretFile = null;
            for (String line : lines) {
                String s = line.trim();
                if (s.startsWith("forwarding-secret-file")) {
                    int eq = s.indexOf('=');
                    if (eq > 0) {
                        String val = s.substring(eq + 1).trim();
                        if (val.startsWith("\"") && val.endsWith("\"")) {
                            val = val.substring(1, val.length() - 1);
                        }
                        secretFile = val;
                        break;
                    }
                }
            }
            if (secretFile != null && !secretFile.isBlank()) {
                java.nio.file.Path secretPath = root.resolve(secretFile);
                if (java.nio.file.Files.exists(secretPath)) {
                    String sec = java.nio.file.Files.readString(secretPath).trim();
                    if (!sec.isEmpty()) return sec;
                }
            }
            // fallback: try find 'secret =' directly (older style)
            for (String line : lines) {
                String s = line.trim();
                if (s.startsWith("secret")) {
                    int eq = s.indexOf('=');
                    if (eq > 0) {
                        String val = s.substring(eq + 1).trim();
                        if (val.startsWith("\"") && val.endsWith("\"")) {
                            val = val.substring(1, val.length() - 1);
                        }
                        if (!val.isEmpty()) return val;
                    }
                }
            }
        } catch (Exception ignored) {}
        return token; // use explicit token if auto not found
    }

    // Robust boolean reader: supports true/false and string "true"/"false"
    private static boolean getBool(org.simpleyaml.configuration.file.YamlConfiguration cfg, String key, boolean def) {
        Object v = cfg.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) {
            String t = s.trim().toLowerCase();
            if (t.equals("true")) return true;
            if (t.equals("false")) return false;
        }
        return def;
    }
}
