package ua.co.tensa.modules.bridge;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.simpleyaml.configuration.file.YamlConfiguration;
import ua.co.tensa.Message;
import ua.co.tensa.config.data.BridgeYAML;
import ua.co.tensa.modules.Modules;

public class PMBridgeDebugCommand implements SimpleCommand {
    @Override
    public void execute(Invocation invocation) {
        CommandSource src = invocation.source();
        if (!hasPermission(invocation)) {
            Message.send(src, "<red>No permission.</red>");
            return;
        }
        // Only allow when PM-Bridge module is enabled
        var entry = ua.co.tensa.modules.Modules.getEntries().get("pm-bridge");
        if (entry == null || !entry.isEnabled()) {
            Message.send(src, "<red>PM-Bridge module is disabled.</red>");
            return;
        }
        // Console only
        if (!src.equals(ua.co.tensa.Tensa.server.getConsoleCommandSource())) {
            Message.send(src, "<red>Console-only command.</red>");
            return;
        }
        // Show current active config (no implicit reload)
        YamlConfiguration cfg = BridgeYAML.getInstance().getConfig();
        var a = BridgeYAML.getInstance().adapter();
        String channel = a.getString("channel", "tensa:exec");
        // raw token from config.yml and resolved token (after use_velocity_secret logic)
        Object rawTokenObj = cfg.get("token");
        String rawToken = rawTokenObj == null ? null : String.valueOf(rawTokenObj);
        String token = PMBridgeModule.resolveToken(cfg);
        String safeToken = ua.co.tensa.Message.escapeMiniMessage(token);
        boolean use = a.getBoolean("use_velocity_secret", true);
        boolean log = a.getBoolean("log", true);
        java.util.List<String> allow = a.getStringList("allow_from");
        boolean enabled = Modules.getEntries().getOrDefault("pm-bridge", null) != null && Modules.getEntries().get("pm-bridge").isEnabled();

        Message.send(src, "<gold>=== PM Bridge Debug ===</gold>");
        Message.send(src, "<gray>Module:</gray> <yellow>" + (enabled ? "enabled" : "disabled") + "</yellow>");
        Message.send(src, "<gray>Channel:</gray> <yellow>" + channel + "</yellow> (<gray>id:</gray> " + MinecraftChannelIdentifier.from(channel).getId() + ")");
        Message.send(src, "<gray>use_velocity_secret:</gray> <yellow>" + use + "</yellow>");
        Message.send(src, "<gray>token (resolved):</gray> <white>" + (safeToken == null ? "<null>" : safeToken) + "</white>");
        Message.send(src, "token (raw cfg): <gray>" + (rawToken == null ? "<null>" : ua.co.tensa.Message.escapeMiniMessage(rawToken) + " [len=" + rawToken.length() + "]") + "</gray>");
        try {
            java.nio.file.Path bridgePath = ua.co.tensa.Tensa.pluginPath.toAbsolutePath().normalize().resolve("bridge.yml");
            Message.send(src, "<gray>bridge.yml:</gray> <yellow>" + bridgePath + "</yellow>");
            java.nio.file.Path pluginsDir = ua.co.tensa.Tensa.pluginPath.toAbsolutePath().normalize(); // .../plugins/TENSA
            java.nio.file.Path root = pluginsDir.getParent() != null ? pluginsDir.getParent().getParent() : null;
            if (root == null) {
                root = java.nio.file.Paths.get(".").toAbsolutePath().normalize();
            }
            if (root != null) {
                java.nio.file.Path velocityToml = root.resolve("velocity.toml");
                Message.send(src, "<gray>velocity.toml:</gray> <yellow>" + velocityToml + "</yellow> exists=" + java.nio.file.Files.exists(velocityToml));
                java.nio.file.Path directSecret = root.resolve("forwarding.secret");
                Message.send(src, "<gray>forwarding.secret:</gray> <yellow>" + directSecret + "</yellow> exists=" + java.nio.file.Files.exists(directSecret));
                if (java.nio.file.Files.exists(velocityToml)) {
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
                    if (secretFile != null) {
                        java.nio.file.Path secPath = root.resolve(secretFile);
                        Message.send(src, "<gray>forwarding-secret-file:</gray> <yellow>" + secPath + "</yellow> exists=" + java.nio.file.Files.exists(secPath));
                    } else {
                        Message.send(src, "<gray>forwarding-secret-file:</gray> <yellow>not configured</yellow>");
                    }
                }
            }
        } catch (Exception ignored) {}
        Message.send(src, "<gray>log:</gray> <yellow>" + log + "</yellow>");
        Message.send(src, "<gold>=========================</gold>");
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("tensa.pm.debug");
    }
}

// MiniMessage escape moved to ua.co.tensa.Message.escapeMiniMessage

// Shared helpers in command context (duplicate minimal copy to avoid exporting internals)
// removed legacy _CFG helper (adapter used instead)
