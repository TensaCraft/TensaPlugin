package ua.co.tensa;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import ua.co.tensa.config.Lang;
import ua.co.tensa.placeholders.PlaceholderManager;

import java.util.Map;


public class Message {
    // Hardcoded console prefix - used for ALL console messages
    private static final String CONSOLE_PREFIX = "<white>[<dark_aqua><bold>Tensa</bold></dark_aqua>]</white> <gray>";

    // Category prefixes for structured logging
    private static final String PREFIX_INFO = CONSOLE_PREFIX + "<white>[INFO]</white> <gray>";
    private static final String PREFIX_WARN = CONSOLE_PREFIX + "<yellow>[WARN]</yellow> <gold>";
    private static final String PREFIX_ERROR = CONSOLE_PREFIX + "<red>[ERROR]</red> <dark_red>";
    private static final String PREFIX_DEBUG = CONSOLE_PREFIX + "<blue>[DEBUG]</blue> <gray>";

    // Subsystem prefixes
    private static final String PREFIX_MODULE = CONSOLE_PREFIX + "<aqua>[MODULE]</aqua> <gray>";
    private static final String PREFIX_DATABASE = CONSOLE_PREFIX + "<green>[DATABASE]</green> <gray>";
    private static final String PREFIX_PLACEHOLDER = CONSOLE_PREFIX + "<light_purple>[PLACEHOLDER]</light_purple> <gray>";
    private static final String PREFIX_CONFIG = CONSOLE_PREFIX + "<yellow>[CONFIG]</yellow> <gray>";
    private static final String PREFIX_RCON = CONSOLE_PREFIX + "<gold>[RCON]</gold> <gray>";

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final java.util.regex.Pattern LEGACY_COLOR_PATTERN =
            java.util.regex.Pattern.compile("&([0-9a-fk-or])(?![>])", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final Map<Character, String> LEGACY_TO_MINIMESSAGE = Map.ofEntries(
            Map.entry('0', "<black>"),
            Map.entry('1', "<dark_blue>"),
            Map.entry('2', "<dark_green>"),
            Map.entry('3', "<dark_aqua>"),
            Map.entry('4', "<dark_red>"),
            Map.entry('5', "<dark_purple>"),
            Map.entry('6', "<gold>"),
            Map.entry('7', "<gray>"),
            Map.entry('8', "<dark_gray>"),
            Map.entry('9', "<blue>"),
            Map.entry('a', "<green>"),
            Map.entry('b', "<aqua>"),
            Map.entry('c', "<red>"),
            Map.entry('d', "<light_purple>"),
            Map.entry('e', "<yellow>"),
            Map.entry('f', "<white>"),
            Map.entry('k', "<obfuscated>"),
            Map.entry('l', "<bold>"),
            Map.entry('m', "<strikethrough>"),
            Map.entry('n', "<underlined>"),
            Map.entry('o', "<italic>"),
            Map.entry('r', "<reset>")
    );

    private static String langPrefix() {
        String p = Lang.LangConfig.prefix;
        return p == null ? "" : p;
    }

    public static Component convert(String message) {
        if (message == null) return Component.empty();
        String normalized = message.replace('§', '&');
        if (normalized.indexOf('&') >= 0) {
            return MM.deserialize(convertLegacyToMiniMessage(normalized));
        }
        return MM.deserialize(normalized);
    }

    // Unified render with placeholders and formatting, using recipient context if player
    public static Component render(CommandSource recipient, String message) {
        Player player = recipient instanceof Player p ? p : null;
        return PlaceholderManager.resolveComponent(player, message);
    }

    public static void send(CommandSource recipient, String message) {
        if (message == null) {
            return;
        }
        Player player = recipient instanceof Player p ? p : null;
        String[] lines = message.split("\\R", -1);
        java.util.concurrent.CompletableFuture<Void> chain = java.util.concurrent.CompletableFuture.completedFuture(null);
        for (String line : lines) {
            String current = line;
            chain = chain.thenCompose(ignored ->
                    PlaceholderManager.resolveComponentAsync(player, current)
                            .thenAccept(component -> sendResolved(recipient, component)));
        }
        chain.exceptionally(throwable -> {
            warn("Message delivery failed: " + throwable.getMessage());
            return null;
        });
    }

    private static void sendResolved(CommandSource recipient, Component component) {
        if (recipient == null || component == null) {
            return;
        }
        if (Tensa.server == null || Tensa.pluginContainer == null) {
            recipient.sendMessage(component);
            return;
        }
        Tensa.server.getScheduler()
                .buildTask(Tensa.pluginContainer, () -> recipient.sendMessage(component))
                .schedule();
    }

    // Curly-brace template renderer with placeholder + MiniMessage support
    public static void sendTemplate(CommandSource recipient, String template, java.util.Map<String, String> values) {
        if (template == null) return;
        String out = renderTemplateString(template, values);
        send(recipient, out);
    }

    public static String renderTemplateString(String template, java.util.Map<String, String> values) {
        String out = template;
        if (values != null) {
            for (java.util.Map.Entry<String, String> e : values.entrySet()) {
                String k = e.getKey();
                String v = e.getValue() == null ? "" : e.getValue();
                out = out.replace("{" + k + "}", v);
            }
        }
        return out;
    }

    // Percent-delimited template renderer: replaces %key% with map values
    public static String renderPercentString(String template, java.util.Map<String, String> values) {
        if (template == null || values == null || values.isEmpty()) return template;
        String out = template;
        for (java.util.Map.Entry<String, String> e : values.entrySet()) {
            String k = e.getKey();
            String v = e.getValue() == null ? "" : e.getValue();
            out = out.replace("%" + k + "%", v);
        }
        return out;
    }

    // Utility: escape a string so MiniMessage does not interpret it as tags
    public static String escapeMiniMessage(String s) {
        if (s == null) return null;
        return s.replace("\\", "\\\\").replace("<", "\\<");
    }

    private static void sendMessageWithPrefix(String prefixStr, String message) {
        message = prefixStr + message;
        if (Tensa.server == null) return; // test environment/no server
        for (String string : message.split("\\R", -1)) {
            Tensa.server.getConsoleCommandSource().sendMessage(render(Tensa.server.getConsoleCommandSource(), string));
        }
    }

    private static void send(String message) {
        if (Tensa.server == null) return; // test environment/no server
        for (String string : message.split("\\R", -1)) {
            Tensa.server.getConsoleCommandSource().sendMessage(render(Tensa.server.getConsoleCommandSource(), string));
        }
    }

    public static void privateMessage(CommandSource recipient, String message) {
        send(recipient, message);
    }

    // Localization helpers
    public static void sendLang(CommandSource recipient, Lang key, String... replacements) {
        String value = Lang.LangConfig.getCleanText(key.name().toLowerCase());
        if (replacements != null && replacements.length > 1) {
            for (int i = 0; i < replacements.length - 1; i += 2) {
                String k = replacements[i];
                String v = replacements[i + 1];
                if (k != null && v != null) {
                    value = value.replace(k, v);
                }
            }
        }
        String prefix = Lang.LangConfig.prefix;
        String full = (prefix == null || prefix.isEmpty()) ? value : prefix + value;
        send(recipient, full);
    }

    // ========== Console Logging Methods ==========

    public static void info(String message) {
        sendMessageWithPrefix(PREFIX_INFO, message);
    }

    public static void info(String message, boolean removePrefix) {
        if (removePrefix) {
            send(message);
        } else {
            sendMessageWithPrefix(PREFIX_INFO, message);
        }
    }

    public static void warn(String message) {
        sendMessageWithPrefix(PREFIX_WARN, message);
    }

    public static void error(String message) {
        sendMessageWithPrefix(PREFIX_ERROR, message);
    }

    public static void debug(String message) {
        sendMessageWithPrefix(PREFIX_DEBUG, message);
    }

    // ========== Subsystem Logging Methods ==========

    public static void module(String moduleName, String action, String details) {
        String formatted = String.format("<aqua>%s</aqua> <dark_gray>→</dark_gray> <white>%s</white> <dark_gray>|</dark_gray> <gray>%s",
            moduleName, action, details);
        sendMessageWithPrefix(PREFIX_MODULE, formatted);
    }

    public static void database(String action, String details) {
        String formatted = String.format("<white>%s</white> <dark_gray>|</dark_gray> <gray>%s", action, details);
        sendMessageWithPrefix(PREFIX_DATABASE, formatted);
    }

    public static void placeholder(String action, String key) {
        String formatted = String.format("<white>%s</white> <dark_gray>→</dark_gray> <light_purple>%s</light_purple>", action, key);
        sendMessageWithPrefix(PREFIX_PLACEHOLDER, formatted);
    }

    public static void config(String action, String file) {
        String formatted = String.format("<white>%s</white> <dark_gray>→</dark_gray> <yellow>%s</yellow>", action, file);
        sendMessageWithPrefix(PREFIX_CONFIG, formatted);
    }

    public static void rcon(String action, String details) {
        String formatted = String.format("<white>%s</white> <dark_gray>|</dark_gray> <gray>%s", action, details);
        sendMessageWithPrefix(PREFIX_RCON, formatted);
    }

    public static void logHeader() {
        String headerLine = "<blue>========================================================</blue>";
        String version = "<yellow>    Current version: <green>" + Tensa.class.getAnnotation(Plugin.class).version();
        String author = "<yellow>    Author: <green>GIGABAIT";

        info(headerLine);

        info("<green>  _____ _____ _   _ ____    _    ");
        info("<green> |_   _| ____| \\ | / ___|  / \\   ");
        info("<green>   | | |  _| |  \\| \\___ \\ / _ \\  ");
        info("<green>   | | | |___| |\\  |___) / ___ \\ ");
        info("<green>   |_| |_____|_| \\_|____/_/   \\_\\");

        info(version);
        info(author);
        info(headerLine);
    }

    private static String convertLegacyToMiniMessage(String input) {
        java.util.regex.Matcher matcher = LEGACY_COLOR_PATTERN.matcher(input);
        StringBuilder result = new StringBuilder(input.length() + 32);

        while (matcher.find()) {
            char code = Character.toLowerCase(matcher.group(1).charAt(0));
            String replacement = LEGACY_TO_MINIMESSAGE.getOrDefault(code, matcher.group(0));
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }
}
