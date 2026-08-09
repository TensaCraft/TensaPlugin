package ua.co.tensa.modules.scheduler;

import ua.co.tensa.config.model.YamlAdapter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record ScheduledCommandsPlan(List<Task> tasks) {
    private static final int CONFIG_VERSION = 1;
    private static final int MAX_TASKS = 128;
    private static final int MAX_COMMANDS_PER_TASK = 64;
    private static final int MAX_COMMAND_LENGTH = 2_048;
    private static final Duration MAX_DELAY = Duration.ofDays(365);
    private static final Pattern TASK_ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");
    private static final Pattern DURATION = Pattern.compile("([0-9]+)(ms|s|m|h|d)");
    private static final Set<String> TASK_KEYS = Set.of(
            "enabled", "initial_delay", "interval", "mode", "commands"
    );

    ScheduledCommandsPlan {
        tasks = List.copyOf(tasks);
    }

    static ScheduledCommandsPlan from(YamlAdapter yaml) {
        int version = yaml.getInt("config_version", CONFIG_VERSION);
        if (version != CONFIG_VERSION) {
            throw new IllegalStateException(version > CONFIG_VERSION
                    ? "scheduler config_version " + version + " is newer than supported version " + CONFIG_VERSION
                    : "scheduler config_version must be " + CONFIG_VERSION);
        }

        Map<String, Object> section = yaml.getSection("tasks");
        if (section.size() > MAX_TASKS) {
            throw new IllegalStateException("scheduler tasks cannot contain more than " + MAX_TASKS + " entries");
        }

        List<Task> tasks = new ArrayList<>();
        for (Map.Entry<String, Object> entry : section.entrySet()) {
            String id = entry.getKey();
            if (!TASK_ID.matcher(id).matches()) {
                throw new IllegalStateException("scheduler task id '" + id
                        + "' must use lowercase letters, numbers, '_' or '-'");
            }
            if (!(entry.getValue() instanceof Map<?, ?> raw)) {
                throw new IllegalStateException("scheduler task '" + id + "' must be a mapping");
            }
            tasks.add(parseTask(id, raw));
        }
        return new ScheduledCommandsPlan(tasks);
    }

    private static Task parseTask(String id, Map<?, ?> raw) {
        Set<String> keys = new LinkedHashSet<>();
        raw.keySet().forEach(key -> keys.add(String.valueOf(key)));
        keys.removeAll(TASK_KEYS);
        if (!keys.isEmpty()) {
            throw new IllegalStateException("scheduler task '" + id + "' has unknown keys: "
                    + String.join(", ", keys));
        }

        boolean enabled = booleanValue(raw, "enabled", true, id);
        DurationRange initialDelay = DurationRange.parse(
                stringValue(raw, "initial_delay", "0s", id), true, id + ".initial_delay");
        DurationRange interval = DurationRange.parse(
                stringValue(raw, "interval", null, id), false, id + ".interval");
        Mode mode = Mode.parse(stringValue(raw, "mode", "all", id), id);
        List<String> commands = commands(raw.get("commands"), id);
        if (enabled && commands.isEmpty()) {
            throw new IllegalStateException("enabled scheduler task '" + id + "' must contain commands");
        }
        return new Task(id, enabled, initialDelay, interval, mode, commands);
    }

    private static boolean booleanValue(Map<?, ?> raw, String key, boolean fallback, String taskId) {
        Object value = raw.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw new IllegalStateException("scheduler task '" + taskId + "." + key + "' must be boolean");
    }

    private static String stringValue(Map<?, ?> raw, String key, String fallback, String taskId) {
        Object value = raw.get(key);
        if (value == null) {
            if (fallback != null) {
                return fallback;
            }
            throw new IllegalStateException("scheduler task '" + taskId + "' is missing " + key);
        }
        if (value instanceof String string && !string.isBlank()) {
            return string.trim();
        }
        throw new IllegalStateException("scheduler task '" + taskId + "." + key + "' must be a non-empty string");
    }

    private static List<String> commands(Object value, String taskId) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> raw)) {
            throw new IllegalStateException("scheduler task '" + taskId + ".commands' must be a list");
        }
        if (raw.size() > MAX_COMMANDS_PER_TASK) {
            throw new IllegalStateException("scheduler task '" + taskId + "' cannot contain more than "
                    + MAX_COMMANDS_PER_TASK + " commands");
        }
        List<String> commands = new ArrayList<>();
        for (Object item : raw) {
            if (!(item instanceof String command)) {
                throw new IllegalStateException("scheduler task '" + taskId + "' commands must be strings");
            }
            String normalized = command.trim();
            while (normalized.startsWith("/")) {
                normalized = normalized.substring(1).stripLeading();
            }
            if (normalized.isBlank()) {
                throw new IllegalStateException("scheduler task '" + taskId + "' contains an empty command");
            }
            if (normalized.length() > MAX_COMMAND_LENGTH || normalized.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalStateException("scheduler task '" + taskId + "' contains an invalid command");
            }
            commands.add(normalized);
        }
        return List.copyOf(commands);
    }

    enum Mode {
        ALL,
        RANDOM,
        SHUFFLE,
        ROUND_ROBIN;

        private static Mode parse(String value, String taskId) {
            try {
                return valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                throw new IllegalStateException("scheduler task '" + taskId
                        + ".mode' must be all, random, shuffle or round_robin");
            }
        }
    }

    record Task(
            String id,
            boolean enabled,
            DurationRange initialDelay,
            DurationRange interval,
            Mode mode,
            List<String> commands
    ) {
        Task {
            commands = List.copyOf(commands);
        }
    }

    record DurationRange(Duration minimum, Duration maximum) {
        private static DurationRange parse(String source, boolean zeroAllowed, String path) {
            String[] bounds = source.toLowerCase(Locale.ROOT).replace(" ", "").split("\\.\\.", -1);
            if (bounds.length < 1 || bounds.length > 2) {
                throw invalid(path);
            }
            Duration minimum = parseDuration(bounds[0], path);
            Duration maximum = bounds.length == 1 ? minimum : parseDuration(bounds[1], path);
            if ((!zeroAllowed && minimum.compareTo(Duration.ofSeconds(1)) < 0)
                    || maximum.compareTo(minimum) < 0
                    || maximum.compareTo(MAX_DELAY) > 0) {
                throw invalid(path);
            }
            return new DurationRange(minimum, maximum);
        }

        private static Duration parseDuration(String value, String path) {
            Matcher matcher = DURATION.matcher(value);
            if (!matcher.matches()) {
                throw invalid(path);
            }
            try {
                long amount = Long.parseLong(matcher.group(1));
                return switch (matcher.group(2)) {
                    case "ms" -> Duration.ofMillis(amount);
                    case "s" -> Duration.ofSeconds(amount);
                    case "m" -> Duration.ofMinutes(amount);
                    case "h" -> Duration.ofHours(amount);
                    case "d" -> Duration.ofDays(amount);
                    default -> throw invalid(path);
                };
            } catch (ArithmeticException | NumberFormatException failure) {
                throw invalid(path);
            }
        }

        private static IllegalStateException invalid(String path) {
            return new IllegalStateException(path
                    + " must be a duration such as 30s, 5m, 2h or a range such as 10m..20m");
        }
    }
}
