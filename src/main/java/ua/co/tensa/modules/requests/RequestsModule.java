package ua.co.tensa.modules.requests;

import org.simpleyaml.configuration.file.YamlConfiguration;
import org.simpleyaml.configuration.file.YamlFile;
import ua.co.tensa.Tensa;
import ua.co.tensa.Util;
import ua.co.tensa.modules.AbstractModule;
import ua.co.tensa.modules.ModuleEntry;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class RequestsModule extends YamlConfiguration {

    private static final ModuleEntry IMPL = new AbstractModule(
            "request-module", "Requests") {
        @Override protected void onEnable() { RequestsModule.enableImpl(); }
        @Override protected void onDisable() { RequestsModule.disableImpl(); }
    };
    public static final ModuleEntry ENTRY = IMPL;

	private static List<YamlConfiguration> configs;
    private static final Map<YamlConfiguration, String> FILE_NAMES = new HashMap<>();

	private static final String folder = Tensa.pluginPath + File.separator + "requests";

    public static void load() {
		File directory = new File(folder);
		if (!directory.exists()) {
			directory.mkdirs();
			Util.copyFile(folder, "linkaccount.yml");
		}

		List<String> fileNames = getConfigurationFiles(folder);

		configs = new ArrayList<>();
		for (String fileName : fileNames) {
			File file = new File(folder, fileName);
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
        ua.co.tensa.Message.info("Requests module enabled");
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
				Map<String, String> map = new HashMap<>();
				map.put("trigger", trigger);
				map.put("file", FILE_NAMES.getOrDefault(config, "unknown"));
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
		return getConfigurationFiles(folder);
	}

	public static YamlConfiguration config(String filename) {
		for (YamlConfiguration config : configs) {
			if (config.getCurrentPath().equals(filename)) {
				return config;
			}
		}
		return null;
	}
}
