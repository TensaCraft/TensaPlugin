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

    private static final String OLD_PRIVATE_TO_FORMAT = "<hover:show_text:'<gray>Натисніть, щоб відповісти</gray>'><click:suggest_command:'/pm {from} '><#55ff55>{from}</#55ff55></click></hover> <aqua>→</aqua> <#55ff55>Вам</#55ff55><aqua>:</aqua> <aqua>{message}</aqua> <gray>[</gray><hover:show_text:'<gray>Скопіювати текст</gray>'><click:copy_to_clipboard:'{message_payload}'>⧉</click></hover><gray>]</gray> <gray>[</gray><hover:show_text:'<gray>Відповісти з цим текстом</gray>'><click:suggest_command:'/pm {from} {message_payload}'>↻</click></hover><gray>]</gray>";
    private static final String OLD_PRIVATE_FROM_FORMAT = "<#55ff55>Ви</#55ff55> <aqua>→</aqua> <hover:show_text:'<gray>Натисніть, щоб продовжити</gray>'><click:suggest_command:'/pm {target} '><#55ff55>{target}</#55ff55></click></hover><aqua>:</aqua> <aqua>{message}</aqua> <gray>[</gray><hover:show_text:'<gray>Скопіювати текст</gray>'><click:copy_to_clipboard:'{message_payload}'>⧉</click></hover><gray>]</gray> <gray>[</gray><hover:show_text:'<gray>Повторно надіслати</gray>'><click:suggest_command:'/pm {target} {message_payload}'>↻</click></hover><gray>]</gray>";
    private static final String PRIVATE_TO_FORMAT = "<hover:show_text:'<#AAB4CC>Натисніть, щоб відповісти</#AAB4CC>'><click:suggest_command:'/pm {from} '><#F4C15D>{from}</#F4C15D></click></hover> <#55FFFF>→</#55FFFF> <#F4C15D>Вам</#F4C15D><#55FFFF>:</#55FFFF> <#F4F7FF>{message}</#F4F7FF> <#AAB4CC>[</#AAB4CC><hover:show_text:'<#AAB4CC>Скопіювати текст</#AAB4CC>'><click:copy_to_clipboard:'{message_payload}'>⧉</click></hover><#AAB4CC>]</#AAB4CC> <#AAB4CC>[</#AAB4CC><hover:show_text:'<#AAB4CC>Відповісти з цим текстом</#AAB4CC>'><click:suggest_command:'/pm {from} {message_payload}'>↻</click></hover><#AAB4CC>]</#AAB4CC>";
    private static final String PRIVATE_FROM_FORMAT = "<#F4C15D>Ви</#F4C15D> <#55FFFF>→</#55FFFF> <hover:show_text:'<#AAB4CC>Натисніть, щоб продовжити</#AAB4CC>'><click:suggest_command:'/pm {target} '><#F4C15D>{target}</#F4C15D></click></hover><#55FFFF>:</#55FFFF> <#F4F7FF>{message}</#F4F7FF> <#AAB4CC>[</#AAB4CC><hover:show_text:'<#AAB4CC>Скопіювати текст</#AAB4CC>'><click:copy_to_clipboard:'{message_payload}'>⧉</click></hover><#AAB4CC>]</#AAB4CC> <#AAB4CC>[</#AAB4CC><hover:show_text:'<#AAB4CC>Повторно надіслати</#AAB4CC>'><click:suggest_command:'/pm {target} {message_payload}'>↻</click></hover><#AAB4CC>]</#AAB4CC>";
    private static final String LEGACY_PRIVATE_TO_FORMAT = OLD_PRIVATE_TO_FORMAT.replace("{message_payload}", "{message}");
    private static final String LEGACY_PRIVATE_FROM_FORMAT = OLD_PRIVATE_FROM_FORMAT.replace("{message_payload}", "{message}");
    private static final String PRIVATE_COMMANDS = "pm,msg,tell,w,m";
    private static final String LEGACY_PRIVATE_COMMANDS = "msg,tell,w";
    private static final String GLOBAL_FORMAT = "<gradient:#55FFFF:#C792EA>{player}</gradient> <#667085>•</#667085> <#F4F7FF>{message}</#F4F7FF>";
    private static final String STAFF_FORMAT = "<gradient:#55FFFF:#C792EA>✦ Staff</gradient> <#667085>•</#667085> <#55FFFF>{server}</#55FFFF> <#F4C15D>{player}</#F4C15D> <#667085>›</#667085> <#F4F7FF>{message}</#F4F7FF>";
    private static final String ALERT_FORMAT = "<gradient:#FF6B81:#F4C15D>✦ Alert</gradient> <#667085>•</#667085> <#F4F7FF>{message}</#F4F7FF>";

    @CfgKey(value = "enabled", comment = "Enable proxy chat interception and chat commands inside Communications")
    public boolean enabled = true;

    @CfgKey(value = "global", comment = "Global chat channel configuration")
    public Map<String, Object> global = defaults(
            entry("enabled", true),
            entry("native", true),
            entry("command", "g,global,gchat"),
            entry("permission", ""),
            entry("see_all", true),
            entry("format", GLOBAL_FORMAT)
    );

    @CfgKey(value = "staff", comment = "Staff chat channel configuration")
    public Map<String, Object> staff = defaults(
            entry("enabled", true),
            entry("command", "s"),
            entry("permission", "tensa.chat.staff"),
            entry("see_all", false),
            entry("format", STAFF_FORMAT)
    );

    @CfgKey(value = "alert", comment = "Broadcast-style alert channel configuration")
    public Map<String, Object> alert = defaults(
            entry("enabled", true),
            entry("command", "alert"),
            entry("permission", "tensa.chat.alert"),
            entry("see_all", true),
            entry("format", ALERT_FORMAT)
    );

    @CfgKey(value = "private", comment = "Cross-server private messages")
    public Map<String, Object> privateChat = defaults(
            entry("enabled", true),
            entry("type", "private"),
            entry("command", PRIVATE_COMMANDS),
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

    public ChatConfig() { super(CommunicationsConfigBootstrap.CHATS_FILE); }

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
            changed |= migrateKnownDefault(section + ".to_format", OLD_PRIVATE_TO_FORMAT, PRIVATE_TO_FORMAT);
            changed |= migrateKnownDefault(section + ".from_format", OLD_PRIVATE_FROM_FORMAT, PRIVATE_FROM_FORMAT);
            changed |= migrateKnownDefault(section + ".to_format", PRIVATE_TO_FORMAT.replace("{message_payload}", "{message}"), PRIVATE_TO_FORMAT);
            changed |= migrateKnownDefault(section + ".from_format", PRIVATE_FROM_FORMAT.replace("{message_payload}", "{message}"), PRIVATE_FROM_FORMAT);
        }
        changed |= migrateKnownDefault("private.command", LEGACY_PRIVATE_COMMANDS, PRIVATE_COMMANDS);
        changed |= migrateKnownDefault("private.command", "pm,msg,tell,w", PRIVATE_COMMANDS);
        changed |= migrateKnownDefault("global.format", "<color:#f4c15d>{player}</color> <dark_gray>»</dark_gray> <white>{message}</white>", GLOBAL_FORMAT);
        changed |= migrateKnownDefault("staff.format", "<dark_gray>[</dark_gray><color:#ff6b6b>S</color><dark_gray>]</dark_gray> <color:#6edcff>{server}</color> <color:#f4c15d>{player}</color> <dark_gray>></dark_gray> <white>{message}</white>", STAFF_FORMAT);
        changed |= migrateKnownDefault("alert.format", "<dark_gray>[</dark_gray><color:#ff5c5c>УВАГА</color><dark_gray>]</dark_gray> <white>{message}</white>", ALERT_FORMAT);
        for (String section : java.util.List.of("global", "staff", "alert", "private", "reply")) {
            String obsoleteRelayFlag = section + ".relay_to_discord";
            if (contains(obsoleteRelayFlag)) {
                setNodeValue(node(obsoleteRelayFlag), null);
                changed = true;
            }
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
