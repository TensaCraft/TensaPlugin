package ua.co.tensa.modules.chat;

import java.util.UUID;

/** Raw, safe-to-relay proxy chat payload. Formatting is applied per destination. */
public record ProxyChatMessage(
        Origin origin,
        String channel,
        String server,
        UUID playerUuid,
        String playerName,
        String message
) {
    public enum Origin {
        MINECRAFT,
        DISCORD,
        SYSTEM
    }
}
