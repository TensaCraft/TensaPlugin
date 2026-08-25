package ua.co.tensa.modules.discord;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;

import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

final class VelocityDiscordBackendBridge implements AutoCloseable {
    private final ChannelIdentifier channel;
    private final DiscordRuntime runtime;
    private final DiscordServerPolicy servers;
    private final RecentEventDeduplicator deduplicator;
    private final AtomicBoolean closed = new AtomicBoolean();

    VelocityDiscordBackendBridge(DiscordSettings settings, DiscordRuntime runtime) {
        this.channel = MinecraftChannelIdentifier.from(settings.backendEventChannel());
        this.runtime = runtime;
        this.servers = runtime.serverPolicy();
        this.deduplicator = new RecentEventDeduplicator(
                settings.advancementDedupWindow(),
                settings.eventStateCapacity()
        );
    }

    ChannelIdentifier channel() {
        return channel;
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(channel)) {
            return;
        }
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (closed.get() || !(event.getSource() instanceof ServerConnection connection)) {
            return;
        }
        String serverName = connection.getServerInfo().getName();
        if (!servers.includes(serverName)) {
            return;
        }
        try {
            DiscordBackendEvent decoded = DiscordBackendEventCodec.decode(event.getData());
            if (decoded instanceof DiscordBackendEvent.Advancement advancement) {
                if (!advancement.playerUuid().equals(connection.getPlayer().getUniqueId())
                        || !advancement.playerName().equalsIgnoreCase(connection.getPlayer().getUsername())) {
                    return;
                }
                String key = serverName.toLowerCase(Locale.ROOT)
                        + ':' + advancement.playerUuid()
                        + ':' + advancement.advancementKey();
                if (deduplicator.firstOccurrence(key, Instant.now())) {
                    runtime.announceAdvancement(
                            connection.getPlayer().getUsername(),
                            serverName,
                            advancement.title(),
                            advancement.description()
                    );
                }
            } else if (decoded instanceof DiscordBackendEvent.Death death) {
                if (!death.playerUuid().equals(connection.getPlayer().getUniqueId())
                        || !death.playerName().equalsIgnoreCase(connection.getPlayer().getUsername())) {
                    return;
                }
                String key = serverName.toLowerCase(Locale.ROOT)
                        + ':' + death.playerUuid()
                        + ":death:" + Integer.toUnsignedString(death.message().hashCode());
                if (deduplicator.firstOccurrence(key, Instant.now())) {
                    runtime.announceDeath(
                            connection.getPlayer().getUsername(),
                            serverName,
                            death.message()
                    );
                }
            }
        } catch (IllegalArgumentException ignored) {
            // Malformed backend packets are handled and dropped without reaching Discord.
        }
    }

    @Override
    public void close() {
        closed.set(true);
    }
}
