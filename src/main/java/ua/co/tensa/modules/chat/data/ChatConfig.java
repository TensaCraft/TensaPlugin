package ua.co.tensa.modules.chat.data;

import ua.co.tensa.config.model.ConfigModelBase;
import ua.co.tensa.config.model.ann.CfgKey;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Typed entry for chats.yml using the model system.
 * Populates three default sections: global, staff, alert.
 */
public class ChatConfig extends ConfigModelBase {
    private static ChatConfig instance;

    @CfgKey("global")
    public Map<String, Object> global = defaults(
            entry("enabled", true),
            entry("command", "g,global,gchat"),
            entry("permission", ""),
            entry("see_all", true),
            entry("format", "<dark_gray>[<gold>G</gold>]</dark_gray> <green>{player}</green> <gold>=></gold> <white>{message}</white>")
    );

    @CfgKey("staff")
    public Map<String, Object> staff = defaults(
            entry("enabled", true),
            entry("command", "s"),
            entry("permission", "tensa.chat.staff"),
            entry("see_all", false),
            entry("format", "<dark_gray>[<dark_red><bold>S</bold></dark_red>]</dark_gray> <aqua><bold>{server}</bold></aqua> <green><bold>{player}</bold></green> <gold><bold>=></bold></gold> <white><bold>{message}</bold></white>")
    );

    @CfgKey("alert")
    public Map<String, Object> alert = defaults(
            entry("enabled", true),
            entry("command", "alert"),
            entry("permission", ""),
            entry("see_all", true),
            entry("format", "<dark_gray>[<dark_red>ALERT</dark_red>]</dark_gray> <white>{message}</white>")
    );

    private static Map<String, Object> defaults(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Object o : kv) {
            if (o instanceof Object[] arr && arr.length >= 2) {
                m.put(String.valueOf(arr[0]), arr[1]);
            }
        }
        return m;
    }
    private static Object[] entry(String k, Object v) { return new Object[]{k, v}; }

    private ChatConfig() { super("chats.yml"); }
    public static synchronized ChatConfig get() { if (instance == null) instance = new ChatConfig(); return instance; }
}
