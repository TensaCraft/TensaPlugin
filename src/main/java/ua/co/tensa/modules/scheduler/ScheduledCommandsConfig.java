package ua.co.tensa.modules.scheduler;

import ua.co.tensa.config.model.ConfigBase;
import ua.co.tensa.config.model.ann.CfgKey;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ScheduledCommandsConfig extends ConfigBase {
    @CfgKey(value = "config_version", comment = "Scheduled command configuration schema version")
    public int configVersion = 1;

    @CfgKey(
            value = "tasks",
            comment = "Named console command schedules. Modes: all, random, shuffle, round_robin. "
                    + "Durations use ms, s, m, h or d; use 10m..20m for a random range."
    )
    public Map<String, Object> tasks = defaultTasks();

    ScheduledCommandsConfig() {
        super("scheduler/config.yml");
    }

    @Override
    protected boolean strictTypeValidation() {
        return true;
    }

    private static Map<String, Object> defaultTasks() {
        LinkedHashMap<String, Object> example = new LinkedHashMap<>();
        example.put("enabled", false);
        example.put("initial_delay", "1m");
        example.put("interval", "10m..20m");
        example.put("mode", "random");
        example.put("commands", List.of(
                "alert <gold>Welcome to the server!</gold>",
                "alert <yellow>Read the rules with /rules.</yellow>"
        ));

        LinkedHashMap<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("announcements", example);
        return defaults;
    }
}
