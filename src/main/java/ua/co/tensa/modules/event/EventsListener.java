package ua.co.tensa.modules.event;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ListenerBoundEvent;
import com.velocitypowered.api.event.proxy.ListenerCloseEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyPreShutdownEvent;
import com.velocitypowered.api.event.proxy.ProxyReloadEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;

/**
 * Event relay that connects Velocity events to EventManager logic.
 * Registered once by the plugin core.
 */
public final class EventsListener {

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        EventManager.onPreLogin(event);
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        EventManager.onLogin(event);
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
    public void onChooseInitialServer(PlayerChooseInitialServerEvent event) {
        EventManager.onChooseInitialServer(event);
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        EventManager.onServerPreConnect(event);
    }

    @Subscribe
    public void onServerSwitch(ServerConnectedEvent event) {
        EventManager.onServerSwitch(event);
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        EventManager.onServerPostConnect(event);
    }

    @Subscribe
    public void onListenerBound(ListenerBoundEvent event) {
        EventManager.onListenerBound(event);
    }

    @Subscribe
    public void onListenerClose(ListenerCloseEvent event) {
        EventManager.onListenerClose(event);
    }

    @Subscribe
    public void onServerRunning(ProxyInitializeEvent event) {
        EventManager.onServerRunning();
    }

    @Subscribe
    public void onProxyReload(ProxyReloadEvent event) {
        EventManager.onProxyReload();
    }

    @Subscribe
    public void onServerPreStop(ProxyPreShutdownEvent event) {
        EventManager.onServerPreStop();
    }

    @Subscribe
    public void onServerStop(ProxyShutdownEvent event) {
        EventManager.onServerStop();
    }
}

