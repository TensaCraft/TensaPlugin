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
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static ua.co.tensa.Tensa.server;

public class Util {
    public record RegisteredCommand(String primary, String alias, String className, String module,
                                    SimpleCommand handler, CommandMeta metadata) {}

    private static final java.util.LinkedHashMap<String, RegisteredCommand> REGISTERED = new java.util.LinkedHashMap<>();

    public static CompletableFuture<Boolean> executeCommand(final String command) {
        return server.getCommandManager().executeAsync(server.getConsoleCommandSource(), command);
    }

    public static CompletableFuture<Boolean> executeCommand(Player player, final String command) {
        return server.getCommandManager().executeAsync(player, command);
    }

    public static synchronized void registerCommand(String command, String alias, SimpleCommand CommandClass) {
        if (command == null || command.isBlank() || CommandClass == null) {
            return;
        }

        String primary = command.trim();
        String secondary = alias == null ? "" : alias.trim();
        for (RegisteredCommand existing : REGISTERED.values()) {
            if (sameCommand(primary, existing.primary())
                    || sameCommand(primary, existing.alias())
                    || (!secondary.isBlank() && (sameCommand(secondary, existing.primary())
                    || sameCommand(secondary, existing.alias())))) {
                throw new IllegalStateException(
                        "Command collision for '" + primary + "' with " + existing.className()
                );
            }
        }

        CommandManager commandManager = server.getCommandManager();
        if (commandManager.hasCommand(primary)
                || (!secondary.isBlank() && commandManager.hasCommand(secondary))) {
            throw new IllegalStateException("Command alias already belongs to another plugin");
        }
        var builder = commandManager.metaBuilder(primary)
                .plugin(Tensa.pluginContainer);
        if (!secondary.isBlank()) {
            builder = builder.aliases(secondary);
        }
        CommandMeta commandMeta = builder.build();
        commandManager.register(commandMeta, CommandClass);
        String className = CommandClass.getClass().getName();
        String module = inferModuleFromClass(className);
        // Track in registry (deduplicate by primary name)
        REGISTERED.put(primary, new RegisteredCommand(primary, secondary, className, module, CommandClass, commandMeta));
    }
    
    public static synchronized void unregisterCommand(String string) {
        if (string == null || string.isBlank()) {
            return;
        }

        CommandManager commandManager = server.getCommandManager();
        var registeredCommands = REGISTERED.values().stream()
                .filter(command -> sameCommand(string, command.primary()) || sameCommand(string, command.alias()))
                .collect(Collectors.toCollection(ArrayList::new));

        if (registeredCommands.isEmpty()) {
            return;
        }

        for (RegisteredCommand command : registeredCommands) {
            if (commandManager.getCommandMeta(command.primary()) == command.metadata()) {
                commandManager.unregister(command.primary());
            }
            if (command.alias() != null && !command.alias().isBlank()
                    && commandManager.getCommandMeta(command.alias()) == command.metadata()) {
                commandManager.unregister(command.alias());
            }
            REGISTERED.remove(command.primary());
        }
    }

    public static synchronized ArrayList<RegisteredCommand> getRegisteredCommands() {
        return new ArrayList<>(REGISTERED.values());
    }

    private static boolean sameCommand(String left, String right) {
        return left != null && right != null && !left.isBlank() && left.equalsIgnoreCase(right);
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
