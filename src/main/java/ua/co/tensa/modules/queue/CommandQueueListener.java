package ua.co.tensa.modules.queue;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;

public final class CommandQueueListener {
    private final CommandQueueManager manager;

    public CommandQueueListener(CommandQueueManager manager) {
        this.manager = manager;
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        manager.dispatchDueForPlayer(event.getPlayer());
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        manager.dispatchDueForPlayer(event.getPlayer());
    }
}
