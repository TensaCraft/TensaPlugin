package ua.co.tensa.config.data;

import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import ua.co.tensa.config.model.YamlBackedFile;

import java.io.File;

public class LangYAML extends YamlBackedFile {

    private static LangYAML instance;

    private LangYAML() {
        super("lang" + File.separator + getLangFile() + ".yml");
    }

    public static LangYAML getInstance() {
        if (instance == null) {
            instance = new LangYAML();
        }
        return instance;
    }

    /**
     * Ensures that every user language file under plugin data folder has all keys
     * present in the provided template configuration. Missing keys are appended
     * with values from the template to keep files up to date after updates.
     */
    public static void syncAllLanguageFiles(org.simpleyaml.configuration.file.YamlConfiguration template) {
        java.io.File langDir = new java.io.File(Tensa.pluginPath + java.io.File.separator + "lang");
        if (!langDir.exists() || !langDir.isDirectory()) return;
        java.io.File[] files = langDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (files == null) return;

        java.util.Set<String> keys = template.getKeys(true);
        for (java.io.File file : files) {
            try {
                org.simpleyaml.configuration.file.YamlFile yf = new org.simpleyaml.configuration.file.YamlFile(file);
                yf.load();
                boolean changed = false;
                for (String key : keys) {
                    // Only copy simple values (strings, numbers, booleans); skip sections
                    if (template.isConfigurationSection(key)) continue;
                    if (!yf.contains(key)) {
                        yf.set(key, template.get(key));
                        changed = true;
                    }
                }
                if (changed) {
                    yf.save();
                }
            } catch (Exception e) {
                Message.warn("Failed to sync lang file " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    @Override
    protected void populateConfigFile() {
        yamlFile.setHeader(getLangFile().toUpperCase() + " localization file (MiniMessage)");

        // Common
        setConfigValue("prefix", "<white>[<dark_aqua><bold>TENSA</bold></dark_aqua>]</white> <gray>");
        setConfigValue("no_perms", "<red>You do not have permission to use this command</red>");
        setConfigValue("unknown_error", "<red>Unknown error</red>");
        setConfigValue("unknown_request", "<red>Unknown request</red>");
        setConfigValue("error_executing", "<red>Error executing:</red>");
        setConfigValue("no_command", "<red>No such command</red>");
        setConfigValue("reload", "<green>All configurations reloaded</green>");
        setConfigValue("enabled", "<green>enabled</green>");
        setConfigValue("disabled", "<red>disabled</red>");
        setConfigValue("module_status", "<aqua>{module}</aqua> <gold>is</gold> <gray>{status}</gray>");

        // Rcon Manager
        yamlFile.setComment("rcon_manager_reload", "Rcon Manager");
        setConfigValue("rcon_manager_reload", "<green>Rcon Manager configurations reloaded</green>");
        setConfigValue("rcon_auth_error", "<gold>{server}</gold>: <red>Authentication error. Please check your server configuration and ensure the server is available</red>");
        setConfigValue("rcon_io_error", "<gold>{server}</gold>: <red>IO error. Please check your server configuration and ensure the server is available</red>");
        setConfigValue("rcon_unknown_error", "<gold>{server}</gold>: <red>Unknown host error. Please check the server IP address configuration</red>");

        // Rcon Server
        yamlFile.setComment("rcon_server_reload", "Rcon Server");
        setConfigValue("rcon_server_reload", "<green>Rcon Server configurations reloaded</green>");
        setConfigValue("rcon_connect_notify", "<green>Rcon connection from:</green> <gray>[</gray><dark_aqua><bold>{address}</bold></dark_aqua><gray>]</gray> <green>Command:</green> <dark_aqua><bold>{command}</bold></dark_aqua>");
        setConfigValue("rcon_usage", "<gold>Usage:</gold> <yellow>rcon</yellow> <gray>[server/all/reload] [command]</gray>");
        setConfigValue("rcon_empty_command", "<gold>Command is empty!</gold>");
        setConfigValue("rcon_invalid_command_or_server", "<gold>Invalid command or server name</gold>");
        setConfigValue("rcon_response", "<gold>{server}</gold>: <green>{response}</green>");
        setConfigValue("rcon_response_empty", "<gray>There is no response from the server</gray>");

        // Bash/Php modules removed

        // PlayerTime Module
        yamlFile.setComment("player_time_usage", "PlayerTime Module");
        setConfigValue("player_time_usage", "<gold>Usage:</gold> <yellow>tptime</yellow> <gray>[Player]</gray>");
        setConfigValue("player_time", "<green>Your game time:</green> <white>{time}</white>");
        setConfigValue("player_time_other", "<green>Game time {player}:</green> <white>{time}</white>");
        setConfigValue("player_not_found", "<red>{player} not found</red>");
        setConfigValue("player_time_days", " days ");
        setConfigValue("player_time_hours", " hours ");
        setConfigValue("player_time_minutes", " minutes ");
        setConfigValue("player_time_seconds", " seconds ");
        setConfigValue("player_time_top", "<green>Top players by time:</green>");
        setConfigValue("player_time_top_entry", "<green>{position}.</green> <gold>{player}</gold> <gray>-</gray> <white>{time}</white>");

        // Send Module
        yamlFile.setComment("send_usage", "Send Module");
        setConfigValue("send_usage", "<gold>Usage:</gold> <yellow>/psend</yellow> <gray>{player} {server}</gray>");
        setConfigValue("send_success", "<green>Player <white>{player}</white> sent to server <white>{server}</white></green>");
        setConfigValue("server_not_found", "<red>Server {server} not found</red>");

        // Chat Module
        yamlFile.setComment("chat_usage", "Chat Module");
        setConfigValue("chat_usage", "<gold>Usage:</gold> <yellow>/{command}</yellow> <gray>(player) (message)</gray>");

        // Help
        yamlFile.setComment("help", "Help");
        setConfigValue("help",
                "<gold>Available commands:</gold>"
                        + "\n<gold>/tensa</gold> <gray>-</gray> <green>Show help.</green>"
                        + "\n<gold>/tensareload</gold> <gray>-</gray> <green>Reload all configurations.</green>"
                        + "\n<gold>/tensamodules</gold> <gray>-</gray> <green>Show all modules.</green>"
                        + "\n<gold>/tpl</gold> <gray>-</gray> <green>Show all plugins.</green>"
                        + "\n<gold>/tptime</gold> <gray>-</gray> <green>Returns your total playing time.</green>"
                        + "\n<gold>/tptime [player]</gold> <gray>-</gray> <green>Returns the specified player's total playing time.</green>"
                        + "\n<gold>/tptop</gold> <gray>-</gray> <green>Shows the top players by time.</green>"
                        + "\n<gold>/rcon [server/all/reload] [command]</gold> <gray>-</gray> <green>Sends the specified command to the specified server or all servers.</green>"
                        + "\n<gold>/php [script/reload] [args]</gold> <gray>-</gray> <green>Executes the specified PHP script.</green>"
                        + "\n<gold>/bash [script/reload] [args]</gold> <gray>-</gray> <green>Executes the specified Bash script.</green>"
                        + "\n<gold>/rules</gold> <gray>-</gray> <green>Reads the specified text file.</green>"
                        + "\n<gold>/readme</gold> <gray>-</gray> <green>Reads the specified text file.</green>"
                        + "\n<gold>/psend [player/all] [server]</gold> <gray>-</gray> <green>Sends the specified player to the specified server.</green>"
        );
    }

    private static String getLangFile() {
        String lang = Tensa.config != null ? Tensa.config.getLang() : "en";
        if (lang == null || lang.isEmpty()) {
            return "en";
        }
        return lang;
    }
}
