package ua.co.tensa;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import ua.co.tensa.config.Config;
import ua.co.tensa.config.Database;
import ua.co.tensa.config.Lang;
import ua.co.tensa.core.user.UserDataService;
import ua.co.tensa.modules.Modules;
import ua.co.tensa.modules.event.EventManager;
import ua.co.tensa.modules.event.EventsListener;
import ua.co.tensa.modules.event.data.EventsConfig;
import ua.co.tensa.modules.rcon.server.RconServerModule;
import ua.co.tensa.placeholders.PlaceholderManager;
import ua.co.tensa.velocity.VelocityLogCleaner;

import java.nio.file.Path;

@Plugin(
        id = "tensa",
        name = "Tensa",
        version = "3.0.0",
        description = "Tensa - Velocity Content Manager Plugin",
        authors = {"GIGABAIT"},
        dependencies = {
                @Dependency(id = "papiproxybridge", optional = true)
        }
)

public class Tensa {

    public static ProxyServer server;
    public static Path pluginPath;
    public static PluginContainer pluginContainer;
    public static Database database;
    public static UserDataService userData;
    public static Config config;
    private static EventsListener coreEventsListener;

    @Inject
    public Tensa(ProxyServer server, @DataDirectory Path dataDirectory) {
        Tensa.server = server;
        Tensa.pluginPath = dataDirectory;
    }

    public static void loadPlugin() {
        config = new Config();
        initialiseDatabase();
        initialiseUserData();
        Lang.initialise();
        PlaceholderManager.initialise();
        VelocityLogCleaner.cleanOnStartup(config);
        initialiseCoreEvents();
        Modules.load();
        try {
            EventManager.onServerRunning();
        } catch (Throwable ignored) {
        }
    }

    public static void reloadPlugin() {
        closeCoreServices();
        config = config == null ? new Config() : config;
        config.reload();
        initialiseDatabase();
        initialiseUserData();
        Lang.initialise();
        PlaceholderManager.reload();
        EventManager.reload();
        Modules.refresh();
    }

    private static void initialiseDatabase() {
        if (database != null) {
            database.close();
            database = null;
        }
        if (config != null && config.databaseEnable()) {
            database = new Database();
            database.connect();
        }
    }

    private static void initialiseUserData() {
        if (userData != null) {
            userData.close();
            userData = null;
        }
        userData = UserDataService.createFromConfig(database);
    }

    private static void initialiseCoreEvents() {
        EventsConfig.get().reloadCfg();
        EventManager.initialise(pluginPath);
        if (coreEventsListener == null) {
            coreEventsListener = new EventsListener();
            server.getEventManager().register(pluginContainer, coreEventsListener);
        }
    }

    private static void closeCoreServices() {
        if (userData != null) {
            userData.close();
            userData = null;
        }
        if (database != null) {
            database.close();
            database = null;
        }
    }


    @Subscribe
    public void onEnable(ProxyInitializeEvent event) {
        Tensa.pluginContainer = server.getPluginManager().fromInstance(this).orElseThrow(() -> new IllegalStateException("Plugin not found in PluginManager"));
        loadPlugin();
        Message.logHeader();
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        Modules.disableAll();
        RconServerModule.disable();
        EventManager.shutdown();
        closeCoreServices();
        // Shutdown database executor pool
        Database.shutdownExecutor();
    }
}
