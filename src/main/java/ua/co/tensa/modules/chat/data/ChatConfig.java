package ua.co.tensa.modules.chat.data;

import ua.co.tensa.config.model.ConfigBase;
import ua.co.tensa.config.model.ann.CfgKey;
import ua.co.tensa.modules.discord.CommunicationsConfigBootstrap;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Typed entry for chats.yml using the model system.
 * Owns chat channel definitions only. Discord relay and proxy-wide chat
 * transport settings are stored in discord.yml.
 */
public class ChatConfig extends ConfigBase {
    @CfgKey(value = "config_version", comment = "Communications chat configuration schema version")
    public int configVersion = CommunicationsConfigBootstrap.CONFIG_VERSION;

    private static final String PRIVATE_TO_FORMAT = "<hover:show_text:'<gray>Натисніть, щоб відповісти</gray>'><click:suggest_command:'/pm {from} '><#55ff55>{from}</#55ff55></click></hover> <aqua>→</aqua> <#55ff55>Вам</#55ff55><aqua>:</aqua> <aqua>{message}</aqua> <gray>[</gray><hover:show_text:'<gray>Скопіювати текст</gray>'><click:copy_to_clipboard:'{message_payload}'>⧉</click></hover><gray>]</gray> <gray>[</gray><hover:show_text:'<gray>Відповісти з цим текстом</gray>'><click:suggest_command:'/pm {from} {message_payload}'>↻</click></hover><gray>]</gray>";
    private static final String PRIVATE_FROM_FORMAT = "<#55ff55>Ви</#55ff55> <aqua>→</aqua> <hover:show_text:'<gray>Натисніть, щоб продовжити</gray>'><click:suggest_command:'/pm {target} '><#55ff55>{target}</#55ff55></click></hover><aqua>:</aqua> <aqua>{message}</aqua> <gray>[</gray><hover:show_text:'<gray>Скопіювати текст</gray>'><click:copy_to_clipboard:'{message_payload}'>⧉</click></hover><gray>]</gray> <gray>[</gray><hover:show_text:'<gray>Повторно надіслати</gray>'><click:suggest_command:'/pm {target} {message_payload}'>↻</click></hover><gray>]</gray>";
    private static final String LEGACY_PRIVATE_TO_FORMAT = PRIVATE_TO_FORMAT.replace("{message_payload}", "{message}");
    private static final String LEGACY_PRIVATE_FROM_FORMAT = PRIVATE_FROM_FORMAT.replace("{message_payload}", "{message}");

    @CfgKey(value = "enabled", comment = "Enable proxy chat interception and chat commands inside Communications")
    public boolean enabled = true;

    @CfgKey(value = "global", comment = "Global chat channel configuration")
    public Map<String, Object> global = defaults(
            entry("enabled", true),
            entry("native", true),
            entry("command", "g,global,gchat"),
            entry("permission", ""),
            entry("see_all", true),
            entry("relay_to_discord", true),
            entry("format", "<color:#f4c15d>{player}</color> <dark_gray>»</dark_gray> <white>{message}</white>")
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
            entry("to_format", PRIVATE_TO_FORMAT),
            entry("from_format", PRIVATE_FROM_FORMAT)
    );

    @CfgKey(value = "reply", comment = "Reply to the last cross-server private conversation")
    public Map<String, Object> reply = defaults(
            entry("enabled", true),
            entry("type", "reply"),
            entry("command", "r,reply"),
            entry("permission", ""),
            entry("to_format", PRIVATE_TO_FORMAT),
            entry("from_format", PRIVATE_FROM_FORMAT)
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

    public ChatConfig() { super("chats.yml"); }

    @Override
    protected boolean strictTypeValidation() {
        return true;
    }

    @Override
    public synchronized void reloadCfg() {
        super.reloadCfg();
        boolean changed = false;
        for (String section : java.util.List.of("private", "reply")) {
            changed |= migrateKnownDefault(section + ".to_format", LEGACY_PRIVATE_TO_FORMAT, PRIVATE_TO_FORMAT);
            changed |= migrateKnownDefault(section + ".from_format", LEGACY_PRIVATE_FROM_FORMAT, PRIVATE_FROM_FORMAT);
        }
        if (changed) {
            save();
            super.reloadCfg();
        }
    }

    private boolean migrateKnownDefault(String path, String legacyValue, String currentValue) {
        if (!legacyValue.equals(getString(path, ""))) {
            return false;
        }
        setNodeValue(node(path), currentValue);
        return true;
    }
}
