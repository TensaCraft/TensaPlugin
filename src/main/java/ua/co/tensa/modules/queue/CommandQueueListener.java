package ua.co.tensa.modules.queue;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import ua.co.tensa.Tensa;

public final class CommandQueueListener {
    private final CommandQueueManager manager;

    public CommandQueueListener(CommandQueueManager manager) {
        this.manager = manager;
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        dispatchOffEventLoop(event.getPlayer());
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        dispatchOffEventLoop(event.getPlayer());
    }

    private void dispatchOffEventLoop(com.velocitypowered.api.proxy.Player player) {
        Tensa.server.getScheduler()
                .buildTask(Tensa.pluginContainer, () -> manager.dispatchDueForPlayer(player))
                .schedule();
    }
}
