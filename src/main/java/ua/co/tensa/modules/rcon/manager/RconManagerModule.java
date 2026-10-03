package ua.co.tensa.modules.rcon.manager;

import ua.co.tensa.Tensa;
import ua.co.tensa.config.model.YamlConfigPreflight;
import ua.co.tensa.modules.AbstractModule;
import ua.co.tensa.modules.ModuleEntry;
import ua.co.tensa.modules.rcon.data.RconManagerConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class RconManagerModule {
    private static volatile ExecutorService commandExecutor;
    private static final java.util.Set<CompletableFuture<?>> pendingCommands = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static volatile RconManagerSettings settings = RconManagerSettings.empty();

    private static final ModuleEntry IMPL = new AbstractModule(
            "rcon-manager", "Rcon Manager") {
        @Override protected void onEnable() {
            try {
                RconManagerSettings plan = prepare();
                commandExecutor = createExecutor();
                settings = plan;
                ua.co.tensa.modules.AbstractModule.registerCommand("rcon", "trcon", new RconManagerCommand());
            } catch (RuntimeException failure) {
                ua.co.tensa.modules.AbstractModule.unregisterCommands("rcon", "trcon");
                shutdownExecutor();
                settings = RconManagerSettings.empty();
                throw failure;
            }
        }
        @Override protected void onReload() { settings = prepare(); }
        @Override protected boolean restartOnReloadFailure() { return false; }
        @Override protected void onDisable() {
            ua.co.tensa.modules.AbstractModule.unregisterCommands("rcon", "trcon");
            shutdownExecutor();
            settings = RconManagerSettings.empty();
        }
    };

    public static final ModuleEntry ENTRY = IMPL;

    public static boolean serverIs(String server) {
        return connection(server) != null;
    }

    public static List<String> getServers() {
        return new ArrayList<>(settings.servers().keySet());
    }

    public static Integer getPort(String server) {
        RconManagerSettings.RconConnection connection = connection(server);
        return connection == null ? 25575 : connection.port();
    }

    public static String getIP(String server) {
        RconManagerSettings.RconConnection connection = connection(server);
        return connection == null ? "127.0.0.1" : connection.host();
    }

    public static String getPass(String server) {
        RconManagerSettings.RconConnection connection = connection(server);
        return connection == null ? "" : connection.password();
    }

    public static ArrayList<String> getCommandArgs() {
        return new ArrayList<>(settings.tabComplete());
    }

    public static synchronized <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        try {
            CompletableFuture<T> result = CompletableFuture.supplyAsync(supplier, executor());
            pendingCommands.add(result);
            result.whenComplete((value, failure) -> pendingCommands.remove(result));
            return result;
        } catch (RejectedExecutionException rejected) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("RCON runtime is stopped or its command queue is full", rejected)
            );
        }
    }

    public static void enable() { IMPL.enable(); }
    public static void disable() { IMPL.disable(); }

    private static synchronized ExecutorService executor() {
        if (commandExecutor == null || commandExecutor.isShutdown()) {
            throw new RejectedExecutionException("RCON manager is not active");
        }
        return commandExecutor;
    }

    static RconManagerSettings.RconConnection connection(String server) {
        if (server == null) return null;
        return settings.servers().get(server.trim().toLowerCase(java.util.Locale.ROOT));
    }

    private static RconManagerSettings prepare() {
        YamlConfigPreflight.validate(Tensa.pluginPath.resolve("rcon/rcon-manager.yml"));
        RconManagerConfig config = RconManagerConfig.get();
        config.reloadCfg();
        return RconManagerSettings.from(config);
    }

    private static ExecutorService createExecutor() {
        return new ThreadPoolExecutor(
                2,
                2,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(64),
                runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("tensa-rcon-manager-" + thread.threadId());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    private static synchronized void shutdownExecutor() {
        if (commandExecutor == null) {
            return;
        }

        pendingCommands.forEach(command -> command.cancel(true));
        pendingCommands.clear();
        commandExecutor.shutdownNow();
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
