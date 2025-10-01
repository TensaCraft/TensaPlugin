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
    private static final String warningSuffix = "<yellow>[WARNING] <gold>";
    private static final String errorSuffix = "<yellow>[ERROR] <dark_red>";
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
        Player player = recipient instanceof Player ? (Player) recipient : null;
        return PlaceholderManager.resolveComponent(player, message);
    }

    public static void send(CommandSource recipient, String message) {
        if (message == null) {
            return;
        }
        Player player = recipient instanceof Player ? (Player) recipient : null;
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

    public static void info(String message) {
        sendMessageWithPrefix(langPrefix(), message);
    }

    public static void info(String message, boolean removePrefix) {
        if (removePrefix) {
            send(message);
        } else {
            sendMessageWithPrefix(langPrefix(), message);
        }
    }

    public static void warn(String message) {
        sendMessageWithPrefix(langPrefix() + warningSuffix, message);
    }

    public static void error(String message) {
        sendMessageWithPrefix(langPrefix() + errorSuffix, message);
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
