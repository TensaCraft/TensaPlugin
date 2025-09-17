package ua.co.tensa.config;

import net.kyori.adventure.text.Component;
import org.simpleyaml.configuration.file.YamlConfiguration;
import ua.co.tensa.Message;
import ua.co.tensa.config.data.LangYAML;

public enum Lang {
    // Lang keys
    debug("debug"), prefix("prefix"), no_perms("no_perms"), unknown_error("unknown_error"), enabled("enabled"), disabled("disabled"),
    module_status("module_status"), unknown_request("unknown_request"), error_executing("error_executing"), no_command("no_command"), reload("reload"),
    rcon_manager_reload("rcon_manager_reload"), rcon_usage("rcon_usage"), rcon_response("rcon_response"),
    rcon_response_empty("rcon_response_empty"),
    rcon_connect_notify("rcon_connect_notify"), rcon_auth_error("rcon_auth_error"), rcon_io_error("rcon_io_error"),
    rcon_unknown_error("rcon_unknown_error"), rcon_empty_command("rcon_empty_command"), rcon_invalid_command_or_server("rcon_invalid_command_or_server"),
    player_time("player_time"), player_not_found("player_not_found"), player_time_other("player_time_other"), player_time_usage("player_time_usage"),
    player_time_days("player_time_days"), player_time_hours("player_time_hours"), player_time_minutes("player_time_minutes"), player_time_seconds("player_time_seconds"),
    help("help"), player_time_top("player_time_top"), player_time_top_entry("player_time_top_entry"), send_usage("send_usage"), send_success("send_success"),
    server_not_found("server_not_found"), chat_usage("chat_usage"),
    // User meta
    meta_usage("meta_usage"), meta_need_player("meta_need_player"), meta_set_ok("meta_set_ok"), meta_get_ok("meta_get_ok"), meta_deleted_ok("meta_deleted_ok"),
    meta_no_meta("meta_no_meta"), meta_list_header("meta_list_header");

    private final String key;

    Lang(String key) {
        this.key = key;
    }

    public Component get() {
        return LangConfig.getKey(this.key);
    }

    public String getClean() {
        return LangConfig.getCleanText(this.key);
    }

    public Component replace(String... list) {
        return LangConfig.getKey(this.key, list);
    }

    public Component text(String text) {
        return Message.convert(text);
    }

    public static void initialise() {
        LangConfig.initialise();
    }

    public static class LangConfig extends YamlConfiguration {

        public static String prefix;
        private static YamlConfiguration config;


        public static void initialise() {
            config = LangYAML.getInstance().getReloadedFile();
            // After loading the active language file, sync all language files
            LangYAML.syncAllLanguageFiles(config);
            prefix = Lang.prefix.getClean();
        }

        public static Component getKey(String key) {
            if (config == null) {
                return Message.convert(key);
            }
            String value = config.getString(key);
            return value == null ? Message.convert(key) : Message.convert((prefix == null ? "" : prefix) + value);
        }

        public static Component getKey(String key, String[] replaceList) {
            if (config == null) {
                return Message.convert(key);
            }
            String resp = config.getString(key);
            for (int i = 0; i < replaceList.length - 1; i += 2) {
                resp = resp.replace(replaceList[i], replaceList[i + 1]);
            }
            return Message.convert((prefix == null ? "" : prefix) + resp);
        }

        public static String getCleanText(String key) {
            if (config == null) return key;
            String value = config.getString(key);
            return value == null ? key : value;
        }
    }
}
