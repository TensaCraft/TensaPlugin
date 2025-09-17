package ua.co.tensa.modules.text;

import ua.co.tensa.Util;
import ua.co.tensa.modules.AbstractModule;
import ua.co.tensa.modules.ModuleEntry;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static ua.co.tensa.Tensa.pluginPath;


public class TextReaderModule {

    private static final ModuleEntry IMPL = new AbstractModule(
            "text-reader", "Text Reader") {
        @Override protected void onEnable() { TextReaderModule.enableImpl(); }
        @Override protected void onDisable() { TextReaderModule.disableImpl(); }
    };
    public static final ModuleEntry ENTRY = IMPL;

    private static final Path dir = pluginPath.resolve("text");

    public static void load() {
        if (dir.toFile().mkdirs()){
            Util.copyFile(dir.toString(), "rules.txt");
            Util.copyFile(dir.toString(), "readme.txt");
        }
    }

    private static void enableImpl() {
        load();
        for (String cmd: getTxtFileNamesWithoutExtension()) {
            AbstractModule.registerCommand(cmd, cmd, new TextReaderCommand());
        }
    }

    private static void disableImpl() {
        AbstractModule.unregisterCommands(getTxtFileNamesWithoutExtension());
    }

    public static void enable() { IMPL.enable(); }
    public static void disable() { IMPL.disable(); }

    public static String readTxt(String filename) throws IOException {
        return Files.readString(dir.resolve(filename + ".txt"), StandardCharsets.UTF_8);
    }

    public static String[] getTxtFileNamesWithoutExtension() {
        File directory = dir.toFile();
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
}
