package ua.co.tensa.modules;

public final class ModuleStatusLogger {
    private ModuleStatusLogger() {}

    public static void enabled(String id, String title) {
        ua.co.tensa.Message.info(title + " module enabled");
    }

    public static void disabled(String id, String title) {
        ua.co.tensa.Message.info(title + " module disabled");
    }

    public static void reloaded(String id, String title) {
        ua.co.tensa.Message.info(title + " module reloaded");
    }
}

