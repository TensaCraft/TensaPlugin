package ua.co.tensa;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import ua.co.tensa.config.Lang;
import ua.co.tensa.placeholders.PlaceholderManager;


public class Message {
    // Hardcoded console prefix - used for ALL console messages
    private static final String CONSOLE_PREFIX = "<white>[<dark_aqua><bold>TENSA</bold></dark_aqua>]</white> <gray>";

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
    private static final LegacyComponentSerializer LEGACY_AMP = LegacyComponentSerializer.legacyAmpersand();

    private static String langPrefix() {
        String p = Lang.LangConfig.prefix;
        return p == null ? "" : p;
    }

    public static Component convert(String message) {
        if (message == null) return Component.empty();
        if (message.indexOf('&') >= 0 || message.indexOf('§') >= 0) {
            return LEGACY_AMP.deserialize(message.replace('§', '&'));
        }
        return MM.deserialize(message);
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
        String[] lines = message.split("\n");
        java.util.concurrent.CompletableFuture<Void> chain = java.util.concurrent.CompletableFuture.completedFuture(null);
        for (String line : lines) {
            String current = line;
            chain = chain.thenCompose(ignored ->
                    PlaceholderManager.resolveComponentAsync(player, current)
                            .thenAccept(recipient::sendMessage));
        }
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
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void sendMessageWithPrefix(String prefixStr, String message) {
        message = prefixStr + message;
        if (Tensa.server == null) return; // test environment/no server
        for (String string : message.split("\n")) {
            Tensa.server.getConsoleCommandSource().sendMessage(render(Tensa.server.getConsoleCommandSource(), string));
        }
    }

    private static void send(String message) {
        if (Tensa.server == null) return; // test environment/no server
        for (String string : message.split("\n")) {
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
}
