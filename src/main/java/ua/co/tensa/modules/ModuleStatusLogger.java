package ua.co.tensa.modules;

public final class ModuleStatusLogger {
    private ModuleStatusLogger() {}

    public static void enabled(String id, String title) {
        ua.co.tensa.Message.module(title, "ENABLED", "Module activated successfully");
    }

    public static void disabled(String id, String title) {
        ua.co.tensa.Message.module(title, "DISABLED", "Module deactivated");
    }

    public static void reloaded(String id, String title) {
        ua.co.tensa.Message.module(title, "RELOADED", "Module configuration reloaded");
    }
}

