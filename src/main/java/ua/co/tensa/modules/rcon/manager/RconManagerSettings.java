package ua.co.tensa.modules.rcon.manager;

import ua.co.tensa.modules.rcon.data.RconManagerConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

record RconManagerSettings(Map<String, RconConnection> servers, List<String> tabComplete) {
    static RconManagerSettings empty() {
        return new RconManagerSettings(Map.of(), List.of());
    }

    static RconManagerSettings from(RconManagerConfig config) {
        LinkedHashMap<String, RconConnection> targets = new LinkedHashMap<>();
        for (String configuredName : config.serverKeys()) {
            String name = configuredName == null ? "" : configuredName.trim().toLowerCase(Locale.ROOT);
            if (!name.matches("[a-z0-9_-]{1,64}")) {
                throw new IllegalStateException("RCON server name is invalid");
            }
            String ip = config.ip(configuredName, "").trim();
            int port = config.port(configuredName, 25575);
            String password = config.pass(configuredName, "");
            if (ip.isBlank() || ip.length() > 255) {
                throw new IllegalStateException("RCON target address is invalid for " + name);
            }
            if (port < 1 || port > 65_535) {
                throw new IllegalStateException("RCON target port is invalid for " + name);
            }
            if (password == null || password.isBlank()) {
                throw new IllegalStateException("RCON target password is missing for " + name);
            }
            if (targets.putIfAbsent(name, new RconConnection(ip, port, password)) != null) {
                throw new IllegalStateException("Duplicate RCON target: " + name);
            }
        }
        List<String> completions = config.tabComplete == null
                ? List.of()
                : config.tabComplete.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(String::trim)
                        .toList();
        return new RconManagerSettings(
                Collections.unmodifiableMap(targets),
                Collections.unmodifiableList(new ArrayList<>(completions))
        );
    }

    record RconConnection(String host, int port, String password) {
    }
}
