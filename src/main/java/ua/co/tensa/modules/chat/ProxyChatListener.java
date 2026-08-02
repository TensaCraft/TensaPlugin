package ua.co.tensa.modules.chat;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;

final class ProxyChatListener {
    private final ProxyChatService service;

    ProxyChatListener(ProxyChatService service) {
        this.service = service;
    }

    @Subscribe
    public void onPlayerChat(PlayerChatEvent event) {
        if (!service.shouldInterceptNative(event.getPlayer())) {
            return;
        }

        // Do not deny signed chat here: Velocity documents that cancellation can
        // disconnect modern clients. The backend TensaProxy bridge suppresses the
        // local ServerChatEvent after the proxy has fanned the message out.
        service.publishPlayer(event.getPlayer(), event.getMessage(), "global");
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        service.forget(event.getPlayer().getUniqueId());
        ChatCommands.forgetPlayer(event.getPlayer().getUniqueId());
    }
}
