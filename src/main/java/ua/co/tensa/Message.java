package ua.co.tensa;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.plugin.Plugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;


public class Message {
    private static final String prefix = "<white>[<dark_aqua>TENSA<white>] <gray>";
    private static final String warningSuffix = "<yellow>[WARNING] <gold>";
    private static final String errorSuffix = "<yellow>[ERROR] <dark_red>";

    public static Component convert(String message) {
        if (message.contains("<") && message.contains(">")) {
            return MiniMessage.miniMessage().deserialize(message);
        } else {
            return LegacyComponentSerializer.legacyAmpersand().deserialize(message.replace("§", "&"));
        }
    }

    private static void sendMessageWithPrefix(String prefix, String message) {
        message = prefix + message;
        for (String string : message.split("\n")) {
            Tensa.server.getConsoleCommandSource().sendMessage(convert(string));
        }
    }

    private static void send(String message) {
        for (String string : message.split("\n")) {
            Tensa.server.getConsoleCommandSource().sendMessage(convert(string));
        }
    }

    public static void privateMessage(CommandSource recipient, String message) {
        for (String line : message.split("\n")) {
            recipient.sendMessage(convert(line));
        }
    }

    public static void info(String message) {
        sendMessageWithPrefix(prefix, message);
    }

    public static void info(String message, boolean removePrefix) {
        if (removePrefix) {
            send(message);
        } else {
            sendMessageWithPrefix(prefix, message);
        }
    }

    public static void warn(String message) {
        sendMessageWithPrefix(prefix + warningSuffix, message);
    }

    public static void error(String message) {
        sendMessageWithPrefix(prefix + errorSuffix, message);
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
