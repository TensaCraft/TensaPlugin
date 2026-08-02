package ua.co.tensa.modules.chat.data;

import ua.co.tensa.config.model.ConfigBase;
import ua.co.tensa.config.model.ann.CfgKey;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Typed entry for chats.yml using the model system.
 * Populates three default sections: global, staff, alert.
 */
public class ChatConfig extends ConfigBase {
    private static ChatConfig instance;

    @CfgKey(value = "proxy", comment = "Proxy-wide player chat interception and formatting")
    public Map<String, Object> proxy = defaults(
            entry("enabled", true),
            entry("excluded_servers", java.util.List.of("auth", "aero-auth")),
            entry("server_aliases", defaults(
                    entry("aeronautics", "Aeronautics"),
                    entry("neopols", "NeoPols")
            )),
            entry("max_length", 256),
            entry("cooldown_millis", 1500),
            entry("duplicate_window_millis", 15000),
            entry("max_repeated_characters", 4),
            entry("format", "<dark_gray>[</dark_gray><color:#32c8ff>{server}</color><dark_gray>]</dark_gray> <color:#f4c15d>{player}</color> <dark_gray>></dark_gray> <white>{message}</white>"),
            entry("discord_format", "<dark_gray>[</dark_gray><color:#5865f2>Discord</color><dark_gray>]</dark_gray> <color:#7fd7ff>{player}</color> <dark_gray>></dark_gray> <white>{message}</white>"),
            entry("cooldown_message", "<color:#ffb84d>Зачекайте трохи перед наступним повідомленням.</color>"),
            entry("duplicate_message", "<color:#ffb84d>Не надсилайте однакові повідомлення поспіль.</color>")
    );

    @CfgKey(value = "global", comment = "Global chat channel configuration")
    public Map<String, Object> global = defaults(
            entry("enabled", true),
            entry("command", "g,global,gchat"),
            entry("permission", ""),
            entry("see_all", true),
            entry("relay_to_discord", true),
            entry("format", "<dark_gray>[</dark_gray><color:#32c8ff>{server}</color><dark_gray>]</dark_gray> <color:#f4c15d>{player}</color> <dark_gray>></dark_gray> <white>{message}</white>")
    );

    @CfgKey(value = "staff", comment = "Staff chat channel configuration")
    public Map<String, Object> staff = defaults(
            entry("enabled", true),
            entry("command", "s"),
            entry("permission", "tensa.chat.staff"),
            entry("see_all", false),
            entry("relay_to_discord", false),
            entry("format", "<dark_gray>[</dark_gray><color:#ff6b6b>S</color><dark_gray>]</dark_gray> <color:#6edcff>{server}</color> <color:#f4c15d>{player}</color> <dark_gray>></dark_gray> <white>{message}</white>")
    );

    @CfgKey(value = "alert", comment = "Broadcast-style alert channel configuration")
    public Map<String, Object> alert = defaults(
            entry("enabled", true),
            entry("command", "alert"),
            entry("permission", "tensa.chat.alert"),
            entry("see_all", true),
            entry("relay_to_discord", true),
            entry("format", "<dark_gray>[</dark_gray><color:#ff5c5c>УВАГА</color><dark_gray>]</dark_gray> <white>{message}</white>")
    );

    @CfgKey(value = "private", comment = "Cross-server private messages")
    public Map<String, Object> privateChat = defaults(
            entry("enabled", true),
            entry("type", "private"),
            entry("command", "msg,tell,w"),
            entry("permission", ""),
            entry("to_format", "<dark_gray>[</dark_gray><color:#6edcff>PM</color><dark_gray>]</dark_gray> <color:#f4c15d>{from}</color> <dark_gray>></dark_gray> <white>{message}</white>"),
            entry("from_format", "<dark_gray>[</dark_gray><color:#6edcff>PM</color><dark_gray>]</dark_gray> <white>ви</white> <dark_gray>></dark_gray> <color:#f4c15d>{to}</color><dark_gray>:</dark_gray> <white>{message}</white>")
    );

    @CfgKey(value = "reply", comment = "Reply to the last cross-server private conversation")
    public Map<String, Object> reply = defaults(
            entry("enabled", true),
            entry("type", "reply"),
            entry("command", "r,reply"),
            entry("permission", ""),
            entry("to_format", "<dark_gray>[</dark_gray><color:#6edcff>PM</color><dark_gray>]</dark_gray> <color:#f4c15d>{from}</color> <dark_gray>></dark_gray> <white>{message}</white>"),
            entry("from_format", "<dark_gray>[</dark_gray><color:#6edcff>PM</color><dark_gray>]</dark_gray> <white>ви</white> <dark_gray>></dark_gray> <color:#f4c15d>{to}</color><dark_gray>:</dark_gray> <white>{message}</white>")
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
