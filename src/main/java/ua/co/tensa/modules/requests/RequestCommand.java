package ua.co.tensa.modules.requests;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import ua.co.tensa.Message;
import ua.co.tensa.Util;
import ua.co.tensa.Tensa;
import ua.co.tensa.config.Lang;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import org.simpleyaml.configuration.file.YamlConfiguration;

import java.util.*;

public class RequestCommand implements SimpleCommand {

    private YamlConfiguration config;

    @Override
    public void execute(Invocation invocation) {
        CommandSource sender = invocation.source();
        String[] args = invocation.arguments();
        try {
            config = RequestsModule.configByTrigger(invocation.alias());
            if (config == null) {
                sender.sendMessage(Lang.no_command.get());
                return;
            }
            if (config.get("permission") != null && !hasPermission(sender, config.getString("permission"))) {
                sender.sendMessage(Lang.no_perms.get());
                return;
            }
            runCommand(args, sender);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void runCommand(String[] args, CommandSource sender) throws Exception {
        Map<String, String> params = placeholderPrepare(args, sender);
        Map<String, String> parameters = parsePlaceholders(config.getConfigurationSection("parameters").getMapValues(true), params);

        String url = parsePlaceholder(config.getString("url"), params);
        HttpRequest req = new HttpRequest(url, config.getString("method"), parameters);
        JsonElement resp = req.send();

        if (resp != null) {
            if (!resp.isJsonObject()) {
                Message.error("❌ Invalid response: expected JSON object but got " + (resp.isJsonArray() ? "array" : resp.isJsonPrimitive() ? "primitive" : resp.isJsonNull() ? "null" : "unknown type"));
                Message.error("Raw response from " + url + ": " + resp.toString());
                return;
            }

            Map<String, String> responseParams = new Gson().fromJson(resp, new TypeToken<Map<String, String>>() {
            }.getType());

            if (config.getBoolean("debug")) {
                sender.sendMessage(Message.convert("<gold>-----------------Request Debug-----------------" + "\n<green>URL: <yellow>" + config.getString("url") + "\n<green>Method: <yellow>" + config.getString("method")));

                responseParams.forEach((key, value) -> {
                    sender.sendMessage(Message.convert("<gray>----------------------------------------------"));
                    sender.sendMessage(Message.convert("<green>Key: <yellow>" + key + "\n<green>Value: <yellow>" + value + "\n<green>Placeholder: <yellow>%" + key + "%"));
                });
                sender.sendMessage(Message.convert("<gray>----------------------------------------------"));
            }

            List<String> successCommands = parsePlaceholdersInList(config.getConfigurationSection("response").getStringList("success"), responseParams);

            successCommands = parsePlaceholdersInList(successCommands, params);
            for (String command : successCommands) {
                Util.executeCommand(command);
            }
        } else {
            Message.error("⚠ Received null or invalid JSON response from URL: " + url);

            List<String> errorCmd = parsePlaceholdersInList(config.getConfigurationSection("response").getStringList("failure"), params);
            for (String command : errorCmd) {
                Util.executeCommand(command);
            }
        }
    }


    /**
     * This method prepares the placeholders for the command.
     * It collects the necessary information from the command sender and arguments.
     *
     * @param args   The command arguments.
     * @param sender The command sender.
     * @return A map of placeholders and their corresponding values.
     */
    private Map<String, String> placeholderPrepare(String[] args, CommandSource sender) {
        Map<String, String> params = new HashMap<>();
        if (sender instanceof Player) {
            Player player = (Player) sender;
            params.put("player_name", player.getUsername());
            params.put("player_uuid", player.getUniqueId().toString());
            params.put("player_ip", player.getRemoteAddress().getAddress().toString().replace("/", ""));
            params.put("server", player.getCurrentServer().isPresent() ? player.getCurrentServer().get().getServerInfo().getName() : "Not server connected");
        } else {
            params.put("player_name", "Console");
            params.put("player_uuid", "Console");
            params.put("player_ip", "Proxy");
            params.put("server", "Proxy");
        }
        for (int i = 0; i < args.length; i++) {
            params.put("arg" + (i + 1), args[i]);
        }
        return params;
    }

    /**
     * This method parses the placeholders in a map.
     * It replaces each placeholder in the map values with its corresponding value from the params map.
     *
     * @param map    The map containing the placeholders.
     * @param params The map containing the placeholder values.
     * @return A map with the parsed placeholders.
     */
    private Map<String, String> parsePlaceholders(Map<String, Object> map, Map<String, String> params) {
        Map<String, String> stringMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String value = entry.getValue().toString();
            for (Map.Entry<String, String> param : params.entrySet()) {
                value = value.replace("%" + param.getKey() + "%", param.getValue());
            }
            stringMap.put(entry.getKey(), value);
        }
        return stringMap;
    }

    /**
     * This method parses a single placeholder in a text.
     * It replaces the placeholder in the text with its corresponding value from the params map.
     *
     * @param text   The text containing the placeholder.
     * @param params The map containing the placeholder values.
     * @return The text with the parsed placeholder.
     */
    private String parsePlaceholder(String text, Map<String, String> params) {
        if (text == null || params == null) {
            return text;
        }
        String parsedText = text;
        for (Map.Entry<String, String> param : params.entrySet()) {
            if (param.getKey() == null || param.getValue() == null) {
                continue;
            }
            parsedText = parsedText.replace("%" + param.getKey() + "%", param.getValue());
        }
        return parsedText;
    }

    /**
     * This method parses the placeholders in a list.
     * It replaces each placeholder in the list items with its corresponding value from the params map.
     *
     * @param list   The list containing the placeholders.
     * @param params The map containing the placeholder values.
     * @return A list with the parsed placeholders.
     */
    private List<String> parsePlaceholdersInList(List<String> list, Map<String, String> params) {
        if (list == null || params == null) {
            return Collections.emptyList();
        }
        List<String> parsedList = new ArrayList<>();
        for (String item : list) {
            if (item == null) {
                item = "null";
            }
            String parsedItem = item;
            for (Map.Entry<String, String> param : params.entrySet()) {
                if (param.getKey() == null || param.getValue() == null) {
                    continue;
                }
                parsedItem = parsedItem.replace("%" + param.getKey() + "%", param.getValue());
            }
            parsedList.add(parsedItem);
        }
        return parsedList;
    }

    private boolean hasPermission(final CommandSource sender, String permission) {
        return sender.hasPermission(permission) || sender.hasPermission("TENSA.requests.*");
    }

    public static void unregister() {
        CommandManager manager = Tensa.server.getCommandManager();
        List<Map<String, String>> triggers = RequestsModule.getTriggerToFileMapping();
        for (Map<String, String> triggerMap : triggers) {
            String trigger = triggerMap.get("trigger");
            manager.unregister(trigger);
        }
    }
}
