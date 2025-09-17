package ua.co.tensa.modules.playertime;

import ua.co.tensa.Tensa;
import ua.co.tensa.config.Database;
import ua.co.tensa.config.DatabaseInitializer;
import ua.co.tensa.modules.AbstractModule;
import ua.co.tensa.modules.ModuleEntry;

import java.util.concurrent.TimeUnit;

public class PlayerTimeModule {

    private static final ModuleEntry IMPL = new AbstractModule(
            "player-time", "Player Time") {
        @Override protected void onEnable() { PlayerTimeModule.enableImpl(); }
        @Override protected void onDisable() { PlayerTimeModule.disableImpl(); }
    };
    public static final ModuleEntry ENTRY = IMPL;

    private static void enableImpl() {
        initialize();
    }

    private static void disableImpl() {
        PlayerTimeCommand.unregister();
        PlayerTimeTopCommand.unregister();
    }

    public static void enable() { IMPL.enable(); }
    public static void disable() { IMPL.disable(); }

    public static void initialize() {
        if (Tensa.config == null || !Tensa.config.databaseEnable()){
            ua.co.tensa.Message.warn("The PlayerTime module requires the use of a database, enable it in the configuration file");
            return;
        }
        Database database = Tensa.database;
        if (database.enabled){
            if (!database.tableExists("player_times")){
                DatabaseInitializer databaseInitializer = new DatabaseInitializer(database);
                databaseInitializer.createPlayerTimeTable();
            }
            PlayerTimeTracker timeTracker = new PlayerTimeTracker(database);
            PlayerEventListener eventListener = new PlayerEventListener(timeTracker);
            ((AbstractModule) IMPL).registerListener(eventListener);
            AbstractModule.registerCommand("tplayertime", "tptime", new PlayerTimeCommand(timeTracker));
            AbstractModule.registerCommand("tplayertop", "tptop", new PlayerTimeTopCommand(timeTracker));

            ((AbstractModule) IMPL).scheduleRepeating(timeTracker::updateAllOnlineTimes, 1, 1, TimeUnit.MINUTES);
        } else {
            ua.co.tensa.Message.warn("PlayerTime module. A database connection could not be established");
            disable();
        }
    }

    public static String formatTime(long timeMillis) {
        long seconds = timeMillis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        seconds %= 60;
        minutes %= 60;
        hours %= 24;

        StringBuilder timeBuilder = new StringBuilder();
        String sDay = unitSuffix(ua.co.tensa.config.Lang.player_time_days, "d");
        String sHour = unitSuffix(ua.co.tensa.config.Lang.player_time_hours, "h");
        String sMin = unitSuffix(ua.co.tensa.config.Lang.player_time_minutes, "m");
        String sSec = unitSuffix(ua.co.tensa.config.Lang.player_time_seconds, "s");
        if (days > 0) {
            timeBuilder.append(days).append(sDay);
        }
        if (hours > 0) {
            timeBuilder.append(hours).append(sHour);
        }
        if (minutes > 0) {
            timeBuilder.append(minutes).append(sMin);
        }
        if (seconds > 0 || timeBuilder.isEmpty()) {
            timeBuilder.append(seconds).append(sSec);
        }
        return timeBuilder.toString();
    }

    private static String unitSuffix(ua.co.tensa.config.Lang key, String fallback) {
        try {
            String v = key.getClean();
            if (v == null) return fallback;
            // When Lang is not initialized, getClean returns enum key (with underscores). Fallback then.
            if (v.contains("_")) return fallback;
            return v;
        } catch (Throwable ignored) {
            return fallback;
        }
    }
}
