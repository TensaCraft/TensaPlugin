package ua.co.tensa.modules.discord;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;

import java.util.Optional;

final class DiscordVelocityListener {
    private final DiscordRuntime runtime;
    private final PlayerPresenceTracker presence;

    DiscordVelocityListener(DiscordRuntime runtime, DiscordSettings settings) {
        this.runtime = runtime;
        this.presence = new PlayerPresenceTracker(runtime.serverPolicy(), settings.eventStateCapacity());
    }

    void prime(Player player) {
        runtime.reconcileLinkedRole(player.getUniqueId());
        player.getCurrentServer().ifPresent(connection -> presence.prime(
                player.getUniqueId(),
                connection.getServerInfo().getName()
        ));
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        if (event.getLoginStatus() == DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN) {
            Player player = event.getPlayer();
            presence.disconnected(player.getUniqueId())
                    .ifPresent(transition -> runtime.announceQuit(player.getUsername(), transition.fromServer()));
        }
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        runtime.reconcileLinkedRole(event.getPlayer().getUniqueId());
        Optional<String> current = event.getPlayer().getCurrentServer()
                .map(connection -> connection.getServerInfo().getName());
        if (current.isEmpty()) {
            return;
        }
        presence.connected(event.getPlayer().getUniqueId(), current.orElseThrow())
                .ifPresent(transition -> publishPresence(event.getPlayer(), transition));
    }

    private void publishPresence(Player player, PlayerPresenceTracker.Transition transition) {
        switch (transition.type()) {
            case JOIN -> runtime.announceJoin(player.getUsername(), transition.toServer());
            case SWITCH -> runtime.announceServerSwitch(
                    player.getUsername(), transition.fromServer(), transition.toServer());
            case QUIT -> runtime.announceQuit(player.getUsername(), transition.fromServer());
        }
    }
}
