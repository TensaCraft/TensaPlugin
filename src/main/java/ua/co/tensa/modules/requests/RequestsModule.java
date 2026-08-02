package ua.co.tensa.modules.requests;

import ua.co.tensa.Tensa;
import ua.co.tensa.Util;
import ua.co.tensa.config.model.YamlAdapter;
import ua.co.tensa.config.model.YamlBackedFile;
import ua.co.tensa.config.model.YamlConfigPreflight;
import ua.co.tensa.modules.AbstractModule;
import ua.co.tensa.modules.ModuleEntry;
import ua.co.tensa.modules.runtime.AtomicRuntimeSlot;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class RequestsModule {

    private static final AtomicRuntimeSlot<Plan, Runtime> RUNTIME =
            new AtomicRuntimeSlot<>(RequestsModule::activate, RequestsModule::deactivate);

    private static final ModuleEntry IMPL = new AbstractModule(
            "request-module", "Requests") {
        @Override protected void onEnable() { RUNTIME.start(prepare()); }
        @Override protected void onDisable() {
            RUNTIME.close();
            HttpRequest.shutdown();
        }
        @Override protected void onReload() { RUNTIME.replace(prepare()); }
        @Override protected boolean restartOnReloadFailure() { return false; }
    };
    public static final ModuleEntry ENTRY = IMPL;

    private static volatile List<RequestConfig> configs = List.of();

    private static Path requestsDir() { return Tensa.pluginPath.resolve("requests"); }

    public static void load() {
        configs = loadConfigs();
    }

    private static List<RequestConfig> loadConfigs() {
        File directory = requestsDir().toFile();
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Failed to create requests directory: " + directory);
        }
        if (isDirectoryEmpty(directory)) {
            Util.copyFile(directory.getPath(), "linkaccount.yml");
        }

        List<RequestConfig> loaded = new ArrayList<>();
        for (String fileName : getConfigurationFiles(directory.getPath())) {
            File file = new File(directory, fileName);
            if (file.isFile()) {
                YamlConfigPreflight.validate(file.toPath());
                loaded.add(new RequestConfig(file.toPath(), file.getName()));
            }
        }
        return List.copyOf(loaded);
    }

    private static Plan prepare() {
        List<RequestConfig> loaded = loadConfigs();
        List<Map<String, String>> triggers = triggerMappings(loaded);
        Set<String> unique = new HashSet<>();
        for (Map<String, String> triggerMap : triggers) {
            String trigger = triggerMap.get("trigger").trim().toLowerCase(Locale.ROOT);
            if (!trigger.matches("[a-z0-9_-]{1,64}")) {
                throw new IllegalStateException("Invalid request command trigger in " + triggerMap.get("file"));
            }
            if (!unique.add(trigger)) {
                throw new IllegalStateException("Duplicate request command trigger: " + trigger);
            }
        }
        return new Plan(loaded, triggers);
    }

    private static boolean isDirectoryEmpty(File directory) {
        String[] entries = directory.list();
        return entries != null && entries.length == 0;
    }

    private static Runtime activate(Plan plan) {
        List<String> registered = new ArrayList<>();
        configs = plan.configs();
        try {
            for (Map<String, String> triggerMap : plan.triggers()) {
                String trigger = triggerMap.get("trigger");
                AbstractModule.registerCommand(trigger, "", new RequestCommand());
                registered.add(trigger);
            }
            return new Runtime(List.copyOf(registered));
        } catch (RuntimeException failure) {
            AbstractModule.unregisterCommands(registered.toArray(String[]::new));
            configs = List.of();
            throw failure;
        }
    }

    private static void deactivate(Runtime runtime) {
        if (runtime != null) {
            AbstractModule.unregisterCommands(runtime.commands().toArray(String[]::new));
        }
        configs = List.of();
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
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    public static List<Map<String, String>> getTriggerToFileMapping() {
        return triggerMappings(configs);
    }

    private static List<Map<String, String>> triggerMappings(List<RequestConfig> source) {
        List<Map<String, String>> result = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return result;
        }
        for (RequestConfig config : source) {
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

    private record Plan(List<RequestConfig> configs, List<Map<String, String>> triggers) {
    }

    private record Runtime(List<String> commands) {
    }
}
