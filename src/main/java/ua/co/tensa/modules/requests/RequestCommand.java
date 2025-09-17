package ua.co.tensa.modules.requests;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import org.simpleyaml.configuration.file.YamlConfiguration;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import ua.co.tensa.Util;
import ua.co.tensa.config.Lang;

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
                Message.sendLang(sender, Lang.no_command);
                return;
            }
            if (config.get("permission") != null && !hasPermission(sender, config.getString("permission"))) {
                Message.sendLang(sender, Lang.no_perms);
                return;
            }
            runCommand(args, sender);
        } catch (Exception e) {
            Message.error("Requests: execution error - " + e.getMessage());
        }
    }

    private void runCommand(String[] args, CommandSource sender) throws Exception {
        Map<String, String> params = placeholderPrepare(args, sender);
        java.util.Map<String, Object> rawParams = config.getConfigurationSection("parameters") != null
                ? config.getConfigurationSection("parameters").getMapValues(true)
                : java.util.Collections.emptyMap();
        Map<String, String> parameters = parsePlaceholders(rawParams, params);

        // Optional per-request restriction: require a player sender
        if (config.contains("require_player") && config.getBoolean("require_player") && !(sender instanceof Player)) {
            Message.info("This request requires a player context. Skipping.", true);
            return;
        }

        String url = parsePlaceholder(config.getString("url"), params);
        HttpRequest req = new HttpRequest(url, config.getString("method"), parameters);
        JsonElement resp = req.send();

        if (resp != null) {
            if (!resp.isJsonObject()) {
                Message.error("Requests: Invalid JSON object in response (type="
                        + (resp.isJsonArray() ? "array" : resp.isJsonPrimitive() ? "primitive" : resp.isJsonNull() ? "null" : "unknown") + ")");
                Message.error("Requests: Raw response from " + url + ": " + resp.toString());
                return;
            }

            Map<String, String> responseParams = new Gson().fromJson(resp, new TypeToken<Map<String, String>>() {
            }.getType());

            if (config.getBoolean("debug")) {
                StringBuilder dbg = new StringBuilder();
                dbg.append("<gold>—— Request Debug ——\n");
                dbg.append("<green>URL: <yellow>").append(url).append("\n");
                dbg.append("<green>Method: <yellow>").append(config.getString("method", "GET")).append("\n");
                if (!parameters.isEmpty()) {
                    dbg.append("<green>Params:\n");
                    parameters.forEach((k,v) -> {
                        String shown = isSensitiveKey(k) ? mask(v) : v;
                        dbg.append("  <gray>").append(k).append("<yellow>=").append(shown).append("\n");
                    });
                }
                if (!responseParams.isEmpty()) {
                    dbg.append("<green>Response placeholders:\n");
                    // Avoid any placeholder engines touching sample tokens by doubling '%'
                    responseParams.forEach((k,v) -> dbg.append("  <gray>").append(k)
                            .append("<yellow>=</yellow>").append(v)
                            .append(" <dark_gray>(%%" + k + "%%)</dark_gray>\n"));
                }
                String out = dbg.toString().trim();
                // Always use Message.send to ensure consistent formatting
                Message.send(sender, out);
            }

        // First apply response placeholders only
        List<String> successTemplates = parsePlaceholdersInList(
                config.getConfigurationSection("response").getStringList("success"), responseParams);

        // If executed from console, skip commands that require a player placeholder
        boolean isPlayer = sender instanceof Player;
        List<String> filteredSuccess = new ArrayList<>();
        for (String cmd : successTemplates) {
            if (!isPlayer && cmd.contains("%player_name%")) {
                Message.warn("Requests: skipped player-targeted command in console context: " + cmd);
                continue;
            }
            filteredSuccess.add(cmd);
        }

        List<String> successCommands = parsePlaceholdersInList(filteredSuccess, params);
        boolean translate = !config.contains("translate_legacy_colors") || config.getBoolean("translate_legacy_colors");
        for (String command : successCommands) {
            dispatchCommand(sender, command, translate);
        }
        } else {
            Message.warn("Requests: null/invalid JSON response from URL: " + url);

            List<String> failureTemplates = parsePlaceholdersInList(
                    config.getConfigurationSection("response").getStringList("failure"), java.util.Collections.emptyMap());
            boolean isPlayer = sender instanceof Player;
            List<String> filteredFailure = new ArrayList<>();
            for (String cmd : failureTemplates) {
                if (!isPlayer && cmd.contains("%player_name%")) {
                    Message.warn("Requests: skipped player-targeted command in console context: " + cmd);
                    continue;
                }
                filteredFailure.add(cmd);
            }
            List<String> errorCmd = parsePlaceholdersInList(filteredFailure, params);
            boolean translate = !config.contains("translate_legacy_colors") || config.getBoolean("translate_legacy_colors");
            for (String command : errorCmd) {
                dispatchCommand(sender, command, translate);
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
            // Do not guess player name from first arg when executed from console
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
            value = Message.renderPercentString(value, params);
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
            if (item == null) item = "null";
            parsedList.add(Message.renderPercentString(item, params));
        }
        return parsedList;
    }

    // helpers
    private static boolean isSensitiveKey(String key) {
        if (key == null) return false;
        String k = key.toLowerCase();
        return k.contains("key") || k.contains("token") || k.contains("secret");
    }
    private static String mask(String value) {
        if (value == null || value.length() <= 4) return "****";
        int show = Math.min(4, value.length());
        return value.substring(0, show) + "***";
    }

    private boolean hasPermission(final CommandSource sender, String permission) {
        return sender.hasPermission(permission) || sender.hasPermission("TENSA.requests.*");
    }

    // Smart dispatcher: handle in-plugin private messages with proper formatting, else fall back to command execution
    private void dispatchCommand(CommandSource sender, String command, boolean translateLegacy) {
        if (command == null || command.isBlank()) return;
        String cmd = command.trim();
        String lower = cmd.toLowerCase(Locale.ROOT);
        if (lower.startsWith("pm ")) {
            String rest = cmd.substring(3).trim();
            int sp = rest.indexOf(' ');
            if (sp > 0) {
                String target = rest.substring(0, sp).trim();
                String message = rest.substring(sp + 1).trim();
                // Send directly through Message to preserve formatting (MiniMessage/legacy)
                java.util.Optional<Player> p = Tensa.server.getPlayer(target);
                if (p.isPresent()) {
                    Message.send(p.get(), message);
                    return;
                } else {
                    // If target not online, warn and skip
                    Message.warn("Requests: target not found for pm: " + target);
                    return;
                }
            }
        }
        String toRun = translateLegacy ? cmd.replace('&', '§') : cmd;
        Util.executeCommand(toRun);
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
