package ua.co.tensa.modules.requests;

import org.simpleyaml.configuration.file.YamlConfiguration;
import org.simpleyaml.configuration.file.YamlFile;
import ua.co.tensa.Tensa;
import ua.co.tensa.Util;
import ua.co.tensa.modules.AbstractModule;
import ua.co.tensa.modules.ModuleEntry;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class RequestsModule {

    private static final ModuleEntry IMPL = new AbstractModule(
            "request-module", "Requests") {
        @Override protected void onEnable() { RequestsModule.enableImpl(); }
        @Override protected void onDisable() { RequestsModule.disableImpl(); }
        @Override protected void onReload() { reloadImpl(); }
    };
    public static final ModuleEntry ENTRY = IMPL;

	private static List<YamlConfiguration> configs;
    private static final Map<YamlConfiguration, String> FILE_NAMES = new HashMap<>();

	private static Path requestsDir() { return Tensa.pluginPath.resolve("requests"); }

    public static void load() {
		File directory = requestsDir().toFile();
		if (!directory.exists()) {
			directory.mkdirs();
			Util.copyFile(directory.getPath(), "linkaccount.yml");
		}

		List<String> fileNames = getConfigurationFiles(directory.getPath());

		configs = new ArrayList<>();
            for (String fileName : fileNames) {
                File file = new File(directory, fileName);
			if (file.isFile()) {
				YamlFile config = new YamlFile(file);
				try {
					config.load();
					configs.add(config);
                        FILE_NAMES.put(config, file.getName());
				} catch (IOException e) {
					throw new RuntimeException(e);
				}

			}
		}
	}

    private static void enableImpl() {
        load();
        List<Map<String, String>> triggers = getTriggerToFileMapping();
        for (Map<String, String> triggerMap : triggers) {
            String trigger = triggerMap.get("trigger");
            AbstractModule.registerCommand(trigger, trigger, new RequestCommand());
        }
        // status logging handled centrally
    }

    private static void disableImpl() {
        // Unregister all known triggers
        List<Map<String, String>> triggers = getTriggerToFileMapping();
        for (Map<String, String> triggerMap : triggers) {
            String trigger = triggerMap.get("trigger");
            AbstractModule.unregisterCommands(trigger);
        }
    }

    public static void enable() { IMPL.enable(); }
    public static void disable() { IMPL.disable(); }

	private static List<String> getConfigurationFiles(String directory) {
		return Arrays.stream(new File(directory).listFiles()).filter(File::isFile).map(File::getName)
				.collect(Collectors.toList());
	}

    public static List<Map<String, String>> getTriggerToFileMapping() {
		List<Map<String, String>> result = new ArrayList<>();
		for (YamlConfiguration config : configs) {
			List<String> triggers = config.getStringList("triggers");
			for (String trigger : triggers) {
				Map<String, String> map = Map.of(
					"trigger", trigger,
					"file", FILE_NAMES.getOrDefault(config, "unknown")
				);
				result.add(map);
			}
		}
		return result;
	}

	public static YamlConfiguration configByTrigger(String trigger) {
		for (YamlConfiguration config : configs) {
			List<String> triggers = config.getStringList("triggers");
			if (triggers.contains(trigger)) {
				return config;
			}
		}
		return null;
	}

	public static List<String> getRequestsFiles() {
		return getConfigurationFiles(requestsDir().toString());
	}

	public static YamlConfiguration config(String filename) {
		for (YamlConfiguration config : configs) {
			if (config.getCurrentPath().equals(filename)) {
				return config;
			}
		}
		return null;
	}

    private static void reloadImpl() {
        // Unregister all, reload files, register again
        disableImpl();
        if (configs != null) configs.clear();
        FILE_NAMES.clear();
        enableImpl();
        // status logging handled centrally
    }
}
