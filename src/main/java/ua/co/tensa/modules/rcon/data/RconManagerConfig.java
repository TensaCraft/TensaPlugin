package ua.co.tensa.modules.rcon.data;

import org.simpleyaml.configuration.file.YamlFile;
import ua.co.tensa.config.model.ConfigBase;
import ua.co.tensa.config.model.ann.CfgKey;

import java.util.*;

public class RconManagerConfig extends ConfigBase {
    private static RconManagerConfig instance;

    @CfgKey("servers")
    public Map<String, Object> servers = new LinkedHashMap<>();

    @CfgKey("tab-complete-list")
    public List<String> tabComplete = new ArrayList<>();

    private RconManagerConfig() { super("rcon/rcon-manager.yml"); }
    public static synchronized RconManagerConfig get() { if (instance == null) instance = new RconManagerConfig(); return instance; }

    @SuppressWarnings("unchecked")
    private Map<String, Object> serverSection(String name) {
        Object sec = servers.get(name);
        if (sec instanceof Map) return (Map<String, Object>) sec;
        return Map.of();
    }

    public Set<String> serverKeys() { return servers.keySet(); }

    public String ip(String name, String def) {
        Object v = serverSection(name).get("ip");
        return v == null ? def : String.valueOf(v);
    }

    public int port(String name, int def) {
        Object v = serverSection(name).get("port");
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception ignored) { return def; }
    }

    public String pass(String name, String def) {
        Object v = serverSection(name).get("pass");
        return v == null ? def : String.valueOf(v);
    }

    @Override
    protected boolean shouldWriteDefault(String basePath, Object defaultValue, YamlFile yaml) {
        // Do not seed example servers if user already has servers defined
        if ("servers".equals(basePath) && yaml.contains("servers")) {
            return false;
        }
        return true;
    }
}
