package ua.co.tensa.config.data;

import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import ua.co.tensa.config.model.YamlBackedFile;
import ua.co.tensa.config.model.YamlFileIO;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

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
    public static void syncAllLanguageFiles(CommentedConfigurationNode template) {
        java.io.File langDir = new java.io.File(Tensa.pluginPath + java.io.File.separator + "lang");
        if (!langDir.exists() || !langDir.isDirectory()) return;
        java.io.File[] files = langDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (files == null) return;

        java.util.Set<String> keys = collectKeys(template);
        for (java.io.File file : files) {
            try {
                java.nio.file.Path path = file.toPath();
                YamlConfigurationLoader loader = YamlFileIO.loader(path);
                CommentedConfigurationNode yf = YamlFileIO.load(loader);
                boolean changed = false;
                for (String key : keys) {
                    // Only copy simple values (strings, numbers, booleans); skip sections
                    CommentedConfigurationNode templateNode = node(template, key);
                    if (!templateNode.childrenMap().isEmpty()) continue;
                    CommentedConfigurationNode targetNode = node(yf, key);
                    if (targetNode.virtual() || targetNode.raw() == null) {
                        targetNode.set(templateNode.raw());
                        changed = true;
                    }
                }
                if (changed) {
                    YamlFileIO.saveValidated(loader, yf, path);
                }
            } catch (Exception e) {
                Message.warn("Failed to sync lang file " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    @Override
    protected void populateConfigFile() {
        setHeader(getLangFile().toUpperCase() + " localization file (MiniMessage)");

        // Common
        setConfigValue("prefix", "<white>[<dark_aqua><bold>Tensa</bold></dark_aqua>]</white> <gray>");
        setLocalizedConfigValue("no_perms",
                "<red>You do not have permission to use this command</red>",
                "<red>У вас немає дозволу на використання цієї команди</red>");
        setLocalizedConfigValue("unknown_error",
                "<red>Unknown error</red>",
                "<red>Невідома помилка</red>");
        setLocalizedConfigValue("unknown_request",
                "<red>Unknown request</red>",
                "<red>Невідомий запит</red>");
        setLocalizedConfigValue("error_executing",
                "<red>Error executing:</red>",
                "<red>Помилка виконання:</red>");
        setLocalizedConfigValue("no_command",
                "<red>No such command</red>",
                "<red>Такої команди не існує</red>");
        setLocalizedConfigValue("reload",
                "<green>All configurations reloaded</green>",
                "<green>Усі конфігурації перезавантажено</green>");
        setLocalizedConfigValue("enabled",
                "<green>enabled</green>",
                "<green>увімкнено</green>");
        setLocalizedConfigValue("disabled",
                "<red>disabled</red>",
                "<red>вимкнено</red>");
        setLocalizedConfigValue("module_status",
                "<aqua>{module}</aqua> <gold>is</gold> <gray>{status}</gray>",
                "<aqua>{module}</aqua> <gold>має статус</gold> <gray>{status}</gray>");

        // Rcon Manager
        setComment("rcon_manager_reload", "Rcon Manager");
        setLocalizedConfigValue("rcon_manager_reload",
                "<green>Rcon Manager configurations reloaded</green>",
                "<green>Конфігурації Rcon Manager перезавантажено</green>");
        setLocalizedConfigValue("rcon_auth_error",
                "<gold>{server}</gold>: <red>Authentication error. Please check your server configuration and ensure the server is available</red>",
                "<gold>{server}</gold>: <red>Помилка авторизації. Перевірте конфігурацію сервера та його доступність</red>");
        setLocalizedConfigValue("rcon_io_error",
                "<gold>{server}</gold>: <red>IO error. Please check your server configuration and ensure the server is available</red>",
                "<gold>{server}</gold>: <red>Помилка IO. Перевірте конфігурацію сервера та його доступність</red>");
        setLocalizedConfigValue("rcon_unknown_error",
                "<gold>{server}</gold>: <red>Unknown host error. Please check the server IP address configuration</red>",
                "<gold>{server}</gold>: <red>Невідомий хост. Перевірте IP-адресу сервера в конфігурації</red>");

        // Rcon Server
        setComment("rcon_server_reload", "Rcon Server");
        setLocalizedConfigValue("rcon_server_reload",
                "<green>Rcon Server configurations reloaded</green>",
                "<green>Конфігурації Rcon Server перезавантажено</green>");
        setLocalizedConfigValue("rcon_connect_notify",
                "<green>Rcon connection from:</green> <gray>[</gray><dark_aqua><bold>{address}</bold></dark_aqua><gray>]</gray> <green>Command:</green> <dark_aqua><bold>{command}</bold></dark_aqua>",
                "<green>RCON-підключення від:</green> <gray>[</gray><dark_aqua><bold>{address}</bold></dark_aqua><gray>]</gray> <green>Команда:</green> <dark_aqua><bold>{command}</bold></dark_aqua>");
        setLocalizedConfigValue("rcon_usage",
                "<gold>Usage:</gold> <yellow>rcon</yellow> <gray>[server/all/reload] [command]</gray>",
                "<gold>Використання:</gold> <yellow>rcon</yellow> <gray>[server/all/reload] [command]</gray>");
        setLocalizedConfigValue("rcon_empty_command",
                "<gold>Command is empty!</gold>",
                "<gold>Команда порожня!</gold>");
        setLocalizedConfigValue("rcon_invalid_command_or_server",
                "<gold>Invalid command or server name</gold>",
                "<gold>Некоректна команда або назва сервера</gold>");
        setLocalizedConfigValue("rcon_response",
                "<gold>{server}</gold>: <green>{response}</green>",
                "<gold>{server}</gold>: <green>{response}</green>");
        setLocalizedConfigValue("rcon_response_empty",
                "<gray>There is no response from the server</gray>",
                "<gray>Від сервера немає відповіді</gray>");

        // Bash/Php modules removed

        // PlayerTime Module
        setComment("player_time_usage", "PlayerTime Module");
        setLocalizedConfigValue("player_time_usage",
                "<gold>Usage:</gold> <yellow>tptime</yellow> <gray>[Player]</gray>",
                "<gold>Використання:</gold> <yellow>tptime</yellow> <gray>[Гравець]</gray>");
        setLocalizedConfigValue("player_time",
                "<green>Your game time:</green> <white>{time}</white>",
                "<green>Ваш час гри:</green> <white>{time}</white>");
        setLocalizedConfigValue("player_time_other",
                "<green>Game time {player}:</green> <white>{time}</white>",
                "<green>Час гри {player}:</green> <white>{time}</white>");
        setLocalizedConfigValue("player_not_found",
                "<red>{player} not found</red>",
                "<red>{player} не знайдено</red>");
        setLocalizedConfigValue("player_time_days", " days ", " дн. ");
        setLocalizedConfigValue("player_time_hours", " hours ", " год. ");
        setLocalizedConfigValue("player_time_minutes", " minutes ", " хв. ");
        setLocalizedConfigValue("player_time_seconds", " seconds ", " сек. ");
        setLocalizedConfigValue("player_time_top",
                "<green>Top players by time:</green>",
                "<green>Топ гравців за часом:</green>");
        setLocalizedConfigValue("player_time_top_entry",
                "<green>{position}.</green> <gold>{player}</gold> <gray>-</gray> <white>{time}</white>",
                "<green>{position}.</green> <gold>{player}</gold> <gray>-</gray> <white>{time}</white>");

        // Send Module
        setComment("send_usage", "Send Module");
        setLocalizedConfigValue("send_usage",
                "<gold>Usage:</gold> <yellow>/psend</yellow> <gray>{player} {server}</gray>",
                "<gold>Використання:</gold> <yellow>/psend</yellow> <gray>{player} {server}</gray>");
        setLocalizedConfigValue("send_success",
                "<green>Player <white>{player}</white> sent to server <white>{server}</white></green>",
                "<green>Гравця <white>{player}</white> відправлено на сервер <white>{server}</white></green>");
        setLocalizedConfigValue("server_not_found",
                "<red>Server {server} not found</red>",
                "<red>Сервер {server} не знайдено</red>");

        // Chat Module
        setComment("chat_usage", "Chat Module");
        setLocalizedConfigValue("chat_usage",
                "<gold>Usage:</gold> <yellow>/{command}</yellow> <gray>(player) (message)</gray>",
                "<gold>Використання:</gold> <yellow>/{command}</yellow> <gray>(гравець) (повідомлення)</gray>");

        // User Meta Module
        setComment("meta_usage", "User Meta Module");
        setLocalizedConfigValue("meta_usage",
                "<gold>Usage:</gold> <yellow>/tmeta</yellow> <gray>[set|get|del|list] [player] [key] [value...] [--session]</gray>",
                "<gold>Використання:</gold> <yellow>/tmeta</yellow> <gray>[set|get|del|list] [гравець] [ключ] [значення...] [--session]</gray>");
        setLocalizedConfigValue("meta_need_player",
                "<red>Specify a player when running from console.</red>",
                "<red>Вкажіть гравця, якщо запускаєте з консолі.</red>");
        setLocalizedConfigValue("meta_set_ok",
                "<green>Set meta </green><yellow>{key}</yellow><green> = </green><gray>{value}</gray>",
                "<green>Встановлено мету </green><yellow>{key}</yellow><green> = </green><gray>{value}</gray>");
        setLocalizedConfigValue("meta_get_ok",
                "<green>Meta </green><yellow>{key}</yellow><green> = </green><gray>{value}</gray>",
                "<green>Мета </green><yellow>{key}</yellow><green> = </green><gray>{value}</gray>");
        setLocalizedConfigValue("meta_deleted_ok",
                "<green>Deleted meta </green><yellow>{key}</yellow>",
                "<green>Видалено мету </green><yellow>{key}</yellow>");
        setLocalizedConfigValue("meta_no_meta",
                "<gray>No metadata found.</gray>",
                "<gray>Метаданих не знайдено.</gray>");
        setLocalizedConfigValue("meta_list_header",
                "<yellow>Metadata list:</yellow>",
                "<yellow>Список метаданих:</yellow>");

        // Help
        setComment("help", "Help");
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
        setLocalizedConfigValue("help_desc_tqueue",
                "Queue a console command until the target player is online. Admin subcommands let you inspect and manage the queue.",
                "Поставити консольну команду в чергу, доки цільовий гравець не буде онлайн. Адмін-підкоманди дозволяють переглядати й керувати чергою.");
        setLocalizedConfigValue("help_desc_text_reader", "Read the text file {command}.", "Прочитати текстовий файл {command}.");
        setLocalizedConfigValue("help_desc_request_trigger", "Execute the request trigger from {file}.", "Виконати request-тригер із конфігурації {file}.");
        setLocalizedConfigValue("help_desc_chat_public", "Send a message to chat {command}.", "Надіслати повідомлення в чат {command}.");
        setLocalizedConfigValue("help_desc_chat_private", "Send a private message through {command}.", "Надіслати приватне повідомлення через {command}.");

        // Command Queue
        setComment("queue_usage", "Command Queue");
        setLocalizedConfigValue("queue_usage",
                "<gold>Usage:</gold> <yellow>/{command}</yellow> <gray>[player|uuid] [command...] [-t:seconds]</gray><newline><gray>Admin:</gray> <white>/{command} add|list|read|remove|clear|run|stats</white>",
                "<gold>Використання:</gold> <yellow>/{command}</yellow> <gray>[player|uuid] [command...] [-t:секунди]</gray><newline><gray>Адмін:</gray> <white>/{command} add|list|read|remove|clear|run|stats</white>");
        setLocalizedConfigValue("queue_target_required",
                "<red>Target player or UUID is required.</red>",
                "<red>Потрібно вказати цільового гравця або UUID.</red>");
        setLocalizedConfigValue("queue_command_required",
                "<red>Queued command cannot be empty.</red>",
                "<red>Команда для черги не може бути порожньою.</red>");
        setLocalizedConfigValue("queue_invalid_delay",
                "<red>Delay must be a non-negative number of seconds, for example -t:60.</red>",
                "<red>Затримка має бути невід'ємною кількістю секунд, наприклад -t:60.</red>");
        setLocalizedConfigValue("queue_limit_reached",
                "<red>The queue is full. Increase queue.max_entries or clear old entries.</red>",
                "<red>Черга заповнена. Збільште queue.max_entries або очистіть старі записи.</red>");
        setLocalizedConfigValue("queue_added",
                "<green>Queued command #</green><yellow>{id}</yellow><green> for </green><white>{target}</white><green> with delay </green><aqua>{delay}s</aqua>",
                "<green>Команду #</green><yellow>{id}</yellow><green> додано в чергу для </green><white>{target}</white><green> із затримкою </green><aqua>{delay}с</aqua>");
        setLocalizedConfigValue("queue_list_empty",
                "<yellow>The queue is empty.</yellow>",
                "<yellow>Черга порожня.</yellow>");
        setLocalizedConfigValue("queue_list_header",
                "<gold>Queued commands:</gold> <white>{count}</white> <gray>(filter: {filter})</gray>",
                "<gold>Команди в черзі:</gold> <white>{count}</white> <gray>(фільтр: {filter})</gray>");
        setLocalizedConfigValue("queue_not_found",
                "<red>Queued command #{id} was not found.</red>",
                "<red>Команду в черзі #{id} не знайдено.</red>");
        setLocalizedConfigValue("queue_removed",
                "<green>Removed queued command #</green><yellow>{id}</yellow>",
                "<green>Видалено команду з черги #</green><yellow>{id}</yellow>");
        setLocalizedConfigValue("queue_cleared",
                "<green>Removed </green><white>{count}</white><green> queued commands for </green><white>{target}</white>",
                "<green>Видалено </green><white>{count}</white><green> команд із черги для </green><white>{target}</white>");
        setLocalizedConfigValue("queue_run_offline",
                "<red>Queued command #{id} cannot run yet because the target player is offline.</red>",
                "<red>Команду з черги #{id} ще не можна виконати, бо цільовий гравець офлайн.</red>");
        setLocalizedConfigValue("queue_run_ok",
                "<green>Executed queued command #</green><yellow>{id}</yellow><green> for </green><white>{player}</white>",
                "<green>Виконано команду з черги #</green><yellow>{id}</yellow><green> для </green><white>{player}</white>");
        setLocalizedConfigValue("queue_stats",
                "<gold>Queue stats:</gold> <gray>total=</gray><white>{total}</white> <gray>due=</gray><white>{due}</white> <gray>online=</gray><white>{online}</white>",
                "<gold>Статистика черги:</gold> <gray>усього=</gray><white>{total}</white> <gray>готово=</gray><white>{due}</white> <gray>онлайн=</gray><white>{online}</white>");
    }

    private void setLocalizedConfigValue(String path, String englishDefault, String ukrainianDefault) {
        String localizedValue = isUkrainianLang() ? ukrainianDefault : englishDefault;
        if (!contains(path)) {
            setNodeValue(node(path), localizedValue);
            markDirty();
            return;
        }

        String current = getString(path, null);
        if (current == null) {
            return;
        }

        if (isUkrainianLang() && englishDefault.equals(current)) {
            setNodeValue(node(path), ukrainianDefault);
            markDirty();
            return;
        }

        if (!isUkrainianLang() && ukrainianDefault.equals(current)) {
            setNodeValue(node(path), englishDefault);
            markDirty();
        }
    }

    private static java.util.Set<String> collectKeys(CommentedConfigurationNode root) {
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        collectKeys(root, "", keys);
        return keys;
    }

    private static void collectKeys(CommentedConfigurationNode node, String prefix, java.util.Set<String> keys) {
        for (java.util.Map.Entry<Object, ? extends CommentedConfigurationNode> entry : node.childrenMap().entrySet()) {
            String key = prefix.isBlank() ? String.valueOf(entry.getKey()) : prefix + "." + entry.getKey();
            keys.add(key);
            collectKeys(entry.getValue(), key, keys);
        }
    }

    private static CommentedConfigurationNode node(CommentedConfigurationNode root, String path) {
        if (path == null || path.isBlank()) {
            return root;
        }
        return root.node((Object[]) path.split("\\."));
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
