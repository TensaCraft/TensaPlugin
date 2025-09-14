package ua.co.tensa;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
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
import ua.co.tensa.modules.Modules;
import ua.co.tensa.modules.bridge.data.BridgeYAML;
import ua.co.tensa.modules.chat.data.ChatYAML;
import ua.co.tensa.modules.event.EventManager;
import ua.co.tensa.modules.event.data.EventsYAML;
import ua.co.tensa.modules.rcon.data.RconManagerYAML;
import ua.co.tensa.modules.rcon.data.RconServerYAML;
import ua.co.tensa.modules.rcon.server.RconServerModule;
import ua.co.tensa.placeholders.PlaceholderManager;

import java.nio.file.Path;

@Plugin(
        id = "tensa",
        name = "TENSA",
        version = "1.0.0",
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

    @Inject
    public Tensa(ProxyServer server, @DataDirectory Path dataDirectory) {
        Tensa.server = server;
        Tensa.pluginPath = dataDirectory;
    }

    public static void loadPlugin() {
        Config.initialise();
        Lang.initialise();
        PlaceholderManager.initialise();
        // Ensure all module configs exist and are up-to-date with new defaults
        try {
            ua.co.tensa.config.data.ConfigYAML.getInstance().reload();
            BridgeYAML.getInstance().reload();
            EventsYAML.getInstance().reload();
            ChatYAML.getInstance().reload();
            RconManagerYAML.getInstance().reload();
            RconServerYAML.getInstance().reload();
            ua.co.tensa.config.data.LangYAML.getInstance().reload();
            ua.co.tensa.config.ConfigRegistry.reloadAll();
        } catch (Throwable ignored) { }
        Modules.load();
    }


    @Subscribe
    public void onPlayerJoin(PostLoginEvent event) {
        EventManager.onPlayerJoin(event);
    }

    @Subscribe
    public void onPlayerLeave(DisconnectEvent event) {
        EventManager.onPlayerLeave(event);
    }

    @Subscribe
    public void onPlayerKick(KickedFromServerEvent event) {
        EventManager.onPlayerKick(event);
    }

    @Subscribe
    public void onServerSwitch(ServerConnectedEvent event) {
        EventManager.onServerSwitch(event);
    }

    @Subscribe
    public void onPlayerMessage(PlayerChatEvent event) {}


    @Subscribe
    public void onEnable(ProxyInitializeEvent event) {
        Tensa.pluginContainer = server.getPluginManager().fromInstance(this).orElseThrow(() -> new IllegalStateException("Plugin not found in PluginManager"));
        loadPlugin();
        Message.logHeader();
        try {
            EventManager.onServerRunning(event);
        } catch (Exception e) {
            // continue
        }
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        RconServerModule.disable();
        EventManager.onServerStop(event);
        if (database != null) {
            database.close();
        }
    }
}
