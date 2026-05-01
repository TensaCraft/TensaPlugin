package ua.co.tensa.modules.requests;

import ua.co.tensa.Tensa;
import ua.co.tensa.Util;
import ua.co.tensa.config.model.YamlAdapter;
import ua.co.tensa.config.model.YamlBackedFile;
import ua.co.tensa.modules.AbstractModule;
import ua.co.tensa.modules.ModuleEntry;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RequestsModule {

    private static final ModuleEntry IMPL = new AbstractModule(
            "request-module", "Requests") {
        @Override protected void onEnable() { RequestsModule.enableImpl(); }
        @Override protected void onDisable() { RequestsModule.disableImpl(); }
        @Override protected void onReload() { reloadImpl(); }
    };
    public static final ModuleEntry ENTRY = IMPL;

    private static List<RequestConfig> configs;

    private static Path requestsDir() { return Tensa.pluginPath.resolve("requests"); }

    public static void load() {
        File directory = requestsDir().toFile();
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Failed to create requests directory: " + directory);
        }
        Util.copyFile(directory.getPath(), "linkaccount.yml");

        configs = new ArrayList<>();
        for (String fileName : getConfigurationFiles(directory.getPath())) {
            File file = new File(directory, fileName);
            if (file.isFile()) {
                configs.add(new RequestConfig(file.toPath(), file.getName()));
            }
        }
    }

    private static void enableImpl() {
        load();
        for (Map<String, String> triggerMap : getTriggerToFileMapping()) {
            AbstractModule.registerCommand(triggerMap.get("trigger"), "", new RequestCommand());
        }
    }

    private static void disableImpl() {
        for (Map<String, String> triggerMap : getTriggerToFileMapping()) {
            AbstractModule.unregisterCommands(triggerMap.get("trigger"));
        }
        HttpRequest.shutdown();
    }

    public static void enable() { IMPL.enable(); }
    public static void disable() { IMPL.disable(); }

    private static List<String> getConfigurationFiles(String directory) {
        File[] files = new File(directory).listFiles();
        if (files == null) {
            return List.of();
        }
        return Arrays.stream(files)
                .filter(File::isFile)
                .map(File::getName)
                .filter(name -> name.endsWith(".yml") || name.endsWith(".yaml"))
                .collect(Collectors.toList());
    }

    public static List<Map<String, String>> getTriggerToFileMapping() {
        List<Map<String, String>> result = new ArrayList<>();
        if (configs == null || configs.isEmpty()) {
            return result;
        }
        for (RequestConfig config : configs) {
            for (String trigger : config.getStringList("triggers")) {
                if (trigger == null || trigger.isBlank()) {
                    continue;
                }
                result.add(Map.of("trigger", trigger, "file", config.fileName()));
            }
        }
        return result;
    }

    public static String fileByTrigger(String trigger) {
        if (trigger == null || trigger.isBlank()) {
            return "";
        }
        for (Map<String, String> triggerMap : getTriggerToFileMapping()) {
            if (trigger.equalsIgnoreCase(triggerMap.get("trigger"))) {
                return triggerMap.getOrDefault("file", "");
            }
        }
        return "";
    }

    public static YamlAdapter configByTrigger(String trigger) {
        if (trigger == null || configs == null) {
            return null;
        }
        for (RequestConfig config : configs) {
            if (config.getStringList("triggers").stream().anyMatch(item -> item != null && item.equalsIgnoreCase(trigger))) {
                return config.adapter();
            }
        }
        return null;
    }

    public static List<String> getRequestsFiles() {
        return getConfigurationFiles(requestsDir().toString());
    }

    public static YamlAdapter config(String filename) {
        if (configs == null) {
            return null;
        }
        for (RequestConfig config : configs) {
            if (filename.equals(config.fileName())) {
                return config.adapter();
            }
        }
        return null;
    }

    private static void reloadImpl() {
        disableImpl();
        if (configs != null) {
            configs.clear();
        }
        enableImpl();
    }

    private static final class RequestConfig extends YamlBackedFile {
        private final String fileName;

        private RequestConfig(Path path, String fileName) {
            super(path.toString(), true);
            this.fileName = fileName;
        }

        @Override
        protected void populateConfigFile() {
        }

        private String fileName() {
            return fileName;
        }
    }
}
