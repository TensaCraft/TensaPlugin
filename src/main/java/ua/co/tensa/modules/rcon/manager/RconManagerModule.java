package ua.co.tensa.modules.rcon.manager;

import ua.co.tensa.modules.AbstractModule;
import ua.co.tensa.modules.ModuleEntry;
import ua.co.tensa.modules.rcon.data.RconManagerConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class RconManagerModule {
    private static ExecutorService commandExecutor;

    private static final ModuleEntry IMPL = new AbstractModule(
            "rcon-manager", "Rcon Manager") {
        @Override protected void onEnable() {
            try {
                commandExecutor = createExecutor();
                RconManagerConfig.get().reloadCfg();
                ua.co.tensa.modules.AbstractModule.registerCommand("rcon", "trcon", new RconManagerCommand());
            } catch (Exception e) {
                ua.co.tensa.Message.rcon("MANAGER ENABLE FAILED", e.getClass().getSimpleName() + " → " + e.getMessage());
            }
        }
        @Override protected void onReload() { RconManagerConfig.get().reloadCfg(); }
        @Override protected void onDisable() {
            ua.co.tensa.modules.AbstractModule.unregisterCommands("rcon", "trcon");
            shutdownExecutor();
        }
    };

    public static final ModuleEntry ENTRY = IMPL;

    public static boolean serverIs(String server) {
        String ip = RconManagerConfig.get().ip(server, "");
        return !ip.isEmpty();
    }

    public static List<String> getServers() {
        return new ArrayList<>(RconManagerConfig.get().serverKeys());
    }

    public static Integer getPort(String server) {
        return RconManagerConfig.get().port(server, 25575);
    }

    public static String getIP(String server) {
        return RconManagerConfig.get().ip(server, "127.0.0.1");
    }

    public static String getPass(String server) {
        return RconManagerConfig.get().pass(server, "");
    }

    public static ArrayList<String> getCommandArgs() {
        return new ArrayList<>(RconManagerConfig.get().tabComplete);
    }

    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, executor());
    }

    public static void enable() { IMPL.enable(); }
    public static void disable() { IMPL.disable(); }

    private static ExecutorService executor() {
        if (commandExecutor == null || commandExecutor.isShutdown()) {
            commandExecutor = createExecutor();
        }
        return commandExecutor;
    }

    private static ExecutorService createExecutor() {
        return Executors.newFixedThreadPool(
                2,
                runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("tensa-rcon-manager-" + thread.threadId());
                    thread.setDaemon(true);
                    return thread;
                }
        );
    }

    private static void shutdownExecutor() {
        if (commandExecutor == null) {
            return;
        }

        commandExecutor.shutdown();
        try {
            if (!commandExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                commandExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            commandExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            commandExecutor = null;
        }
    }
}
