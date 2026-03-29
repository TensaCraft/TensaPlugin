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
        setConfigValue("prefix", "<white>[<dark_aqua><bold>Tensa</bold></dark_aqua>]</white> <gray>");
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
        setLocalizedConfigValue("help",
                "<gold>Available commands:</gold>",
                "<gold>Доступні команди:</gold>");
        setLocalizedConfigValue("help_header",
                "<gold>Available commands:</gold>",
                "<gold>Доступні команди:</gold>");
        setLocalizedConfigValue("help_empty",
                "<yellow>No commands registered.</yellow>",
                "<yellow>Зареєстрованих команд не знайдено.</yellow>");
        setLocalizedConfigValue("help_command_format",
                "<gold>{usage}</gold>{aliases} <gray>-</gray> <green>{description}</green>",
                "<gold>{usage}</gold>{aliases} <gray>-</gray> <green>{description}</green>");
        setLocalizedConfigValue("help_alias_format",
                " <dark_gray>(</dark_gray><gray>aliases:</gray> <yellow>{aliases}</yellow><dark_gray>)</dark_gray>",
                " <dark_gray>(</dark_gray><gray>аліаси:</gray> <yellow>{aliases}</yellow><dark_gray>)</dark_gray>");
        setLocalizedConfigValue("help_desc_tensa", "Show help.", "Показати довідку.");
        setLocalizedConfigValue("help_desc_tensareload", "Reload all plugin configurations.", "Перезавантажити всі конфігурації плагіна.");
        setLocalizedConfigValue("help_desc_tensamodules", "Show all configured modules.", "Показати всі налаштовані модулі.");
        setLocalizedConfigValue("help_desc_tpl", "Show installed proxy plugins.", "Показати встановлені плагіни проксі.");
        setLocalizedConfigValue("help_desc_psend", "Send a player to another server.", "Відправити гравця на інший сервер.");
        setLocalizedConfigValue("help_desc_tparse", "Parse placeholders in text.", "Розпарсити плейсхолдери в тексті.");
        setLocalizedConfigValue("help_desc_tinfo", "Show plugin information, modules, and commands.", "Показати інформацію про плагін, модулі та команди.");
        setLocalizedConfigValue("help_desc_tptime", "Show playing time for yourself or another player.", "Показати час гри для себе або іншого гравця.");
        setLocalizedConfigValue("help_desc_tptop", "Show the top players by playing time.", "Показати топ гравців за часом гри.");
        setLocalizedConfigValue("help_desc_rcon", "Execute an RCON command on one or more servers.", "Виконати RCON-команду на одному або кількох серверах.");
        setLocalizedConfigValue("help_desc_tmeta", "Manage temporary and persistent user metadata.", "Керувати тимчасовими та постійними метаданими користувачів.");
        setLocalizedConfigValue("help_desc_tpmdebug", "Show PM-Bridge debug information.", "Показати діагностичну інформацію PM-Bridge.");
        setLocalizedConfigValue("help_desc_text_reader", "Read the text file {command}.", "Прочитати текстовий файл {command}.");
        setLocalizedConfigValue("help_desc_request_trigger", "Execute the request trigger from {file}.", "Виконати request-тригер із конфігурації {file}.");
        setLocalizedConfigValue("help_desc_chat_public", "Send a message to chat {command}.", "Надіслати повідомлення в чат {command}.");
        setLocalizedConfigValue("help_desc_chat_private", "Send a private message through {command}.", "Надіслати приватне повідомлення через {command}.");
    }

    private void setLocalizedConfigValue(String path, String englishDefault, String ukrainianDefault) {
        String localizedValue = isUkrainianLang() ? ukrainianDefault : englishDefault;
        if (!yamlFile.contains(path)) {
            yamlFile.set(path, localizedValue);
            return;
        }

        String current = yamlFile.getString(path);
        if (current == null) {
            return;
        }

        if (isUkrainianLang() && englishDefault.equals(current)) {
            yamlFile.set(path, ukrainianDefault);
            return;
        }

        if (!isUkrainianLang() && ukrainianDefault.equals(current)) {
            yamlFile.set(path, englishDefault);
        }
    }

    private boolean isUkrainianLang() {
        return "uk".equalsIgnoreCase(getLangFile());
    }

    private static String getLangFile() {
        String lang = Tensa.config != null ? Tensa.config.getLang() : "en";
        if (lang == null || lang.isEmpty()) {
            return "en";
        }
        return lang;
    }
}
