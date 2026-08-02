package ua.co.tensa.modules.text;

import ua.co.tensa.Util;
import ua.co.tensa.modules.AbstractModule;
import ua.co.tensa.modules.ModuleEntry;
import ua.co.tensa.modules.runtime.AtomicRuntimeSlot;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static ua.co.tensa.Tensa.pluginPath;


public class TextReaderModule {

    private static final AtomicRuntimeSlot<Plan, Runtime> RUNTIME =
            new AtomicRuntimeSlot<>(TextReaderModule::activate, TextReaderModule::deactivate);

    private static final ModuleEntry IMPL = new AbstractModule(
            "text-reader", "Text Reader") {
        @Override protected void onEnable() { RUNTIME.start(prepare()); }
        @Override protected void onDisable() { RUNTIME.close(); }
        @Override protected void onReload() { RUNTIME.replace(prepare()); }
        @Override protected boolean restartOnReloadFailure() { return false; }
    };
    public static final ModuleEntry ENTRY = IMPL;
    private static Path dir() { return pluginPath.resolve("text"); }

    public static void load() {
        Path dir = dir();
        dir.toFile().mkdirs();
        Util.copyFile(dir.toString(), "rules.txt");
        Util.copyFile(dir.toString(), "readme.txt");
    }

    private static Plan prepare() {
        load();
        List<String> commands = Arrays.stream(getTxtFileNamesWithoutExtension())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        for (String command : commands) {
            if (!command.matches("[A-Za-z0-9_-]{1,64}")) {
                throw new IllegalStateException("Invalid text command filename: " + command);
            }
        }
        return new Plan(commands);
    }

    private static Runtime activate(Plan plan) {
        List<String> registered = new ArrayList<>();
        try {
            for (String command : plan.commands()) {
                AbstractModule.registerCommand(command, "", new TextReaderCommand());
                registered.add(command);
            }
            return new Runtime(List.copyOf(registered));
        } catch (RuntimeException failure) {
            AbstractModule.unregisterCommands(registered.toArray(String[]::new));
            throw failure;
        }
    }

    private static void deactivate(Runtime runtime) {
        if (runtime != null) {
            AbstractModule.unregisterCommands(runtime.commands().toArray(String[]::new));
        }
    }

    public static void enable() { IMPL.enable(); }
    public static void disable() { IMPL.disable(); }

    public static String readTxt(String filename) throws IOException {
        return Files.readString(dir().resolve(filename + ".txt"), StandardCharsets.UTF_8);
    }

    public static String[] getTxtFileNamesWithoutExtension() {
        File directory = dir().toFile();
        if (directory.exists() && directory.isDirectory()) {
            String[] fileNames = directory.list((dir, name) -> name.endsWith(".txt"));
            if (fileNames != null) {
                for (int i = 0; i < fileNames.length; i++) {
                    fileNames[i] = fileNames[i].substring(0, fileNames[i].length() - 4);
                }
                return fileNames;
            }
        }
        return new String[]{};
    }

    private record Plan(List<String> commands) {
    }

    private record Runtime(List<String> commands) {
    }
}
