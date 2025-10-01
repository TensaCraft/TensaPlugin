package ua.co.tensa;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static ua.co.tensa.Tensa.server;

public class Util {
    public record RegisteredCommand(String primary, String alias, String className, String module) {}

    private static final java.util.LinkedHashMap<String, RegisteredCommand> REGISTERED = new java.util.LinkedHashMap<>();

    public static void executeCommand(final String command) {
        server.getCommandManager().executeAsync(server.getConsoleCommandSource(), command);
    }

    public static void executeCommand(Player player, final String command) {
        server.getCommandManager().executeAsync(player, command);
    }

    public static void registerCommand(String command, String alias, SimpleCommand CommandClass) {
        CommandManager commandManager = server.getCommandManager();
        var builder = commandManager.metaBuilder(command)
                .plugin(Tensa.pluginContainer);
        if (alias != null && !alias.isBlank()) {
            builder = builder.aliases(alias);
        }
        CommandMeta commandMeta = builder.build();
        commandManager.register(commandMeta, CommandClass);
        String className = CommandClass.getClass().getName();
        String module = inferModuleFromClass(className);
        // Track in registry (deduplicate by primary name)
        REGISTERED.put(command, new RegisteredCommand(command, alias, className, module));
    }
    
    public static void unregisterCommand(String string) {
        server.getCommandManager().unregister(string);
        REGISTERED.remove(string);
    }

    public static ArrayList<RegisteredCommand> getRegisteredCommands() {
        return new ArrayList<>(REGISTERED.values());
    }

    private static String inferModuleFromClass(String className) {
        String mod = "core";
        String needle = ".modules.";
        int idx = className.indexOf(needle);
        if (idx != -1) {
            String rest = className.substring(idx + needle.length());
            int dot = rest.indexOf('.');
            if (dot > 0) {
                mod = rest.substring(0, dot);
            }
        }
        return mod;
    }

    public static String capitalize(String text) {
        if (text == null || text.isEmpty()) return text;
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }

    public static void copyFile(String toPath, String fileName) {
        File file = new File(toPath + File.separator + fileName);

        if (file.exists()) {
            return;
        }

        try (InputStream input = Tensa.class.getResourceAsStream("/" + file.getName())) {

            if (input != null) {
                Files.copy(input, file.toPath());
            } else {
                file.createNewFile();
            }

        } catch (IOException e) {
            Message.error(e.getMessage());
        }
    }

    public static void createDir(String path) {
        File dir = Path.of(path).toFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }


    public static String getFileExtension(String fileName) {
        if (fileName == null) {
            throw new IllegalArgumentException("fileName must not be null!");
        }

        String extension = "";

        int index = fileName.lastIndexOf('.');
        if (index > 0) {
            extension = fileName.substring(index + 1);
        }

        return extension;

    }

}
