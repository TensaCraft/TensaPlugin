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
import ua.co.tensa.config.data.LangYAML;
import ua.co.tensa.modules.Modules;
import ua.co.tensa.modules.rcon.server.RconServerModule;
import ua.co.tensa.placeholders.PlaceholderManager;

import java.nio.file.Path;

@Plugin(
        id = "tensa",
        name = "TENSA",
        version = "2.0.0",
        description = "TENSA - Velocity Content Manager Plugin",
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
    public static Config config;

    @Inject
    public Tensa(ProxyServer server, @DataDirectory Path dataDirectory) {
        Tensa.server = server;
        Tensa.pluginPath = dataDirectory;
    }

    public static void loadPlugin() {
        config = new Config();
        // Lang and other singletons can use adapter from manager if needed later
        Lang.initialise();
        PlaceholderManager.initialise();
        // Global localization/config init (module configs are handled by modules themselves)
        try { LangYAML.getInstance().reload(); } catch (Throwable ignored) {}
        Modules.load();
    }


    @Subscribe
    public void onEnable(ProxyInitializeEvent event) {
        Tensa.pluginContainer = server.getPluginManager().fromInstance(this).orElseThrow(() -> new IllegalStateException("Plugin not found in PluginManager"));
        loadPlugin();
        Message.logHeader();
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        RconServerModule.disable();
        if (database != null) {
            database.close();
        }
    }
}
