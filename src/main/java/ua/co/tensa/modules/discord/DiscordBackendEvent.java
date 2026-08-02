package ua.co.tensa.modules.discord;

import java.util.UUID;

public sealed interface DiscordBackendEvent {
    record Advancement(
            UUID playerUuid,
            String playerName,
            String advancementKey,
            String title
    ) implements DiscordBackendEvent {
    }
}
