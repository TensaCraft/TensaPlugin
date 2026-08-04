package ua.co.tensa.modules.discord;

import java.util.UUID;

public sealed interface DiscordBackendEvent {
    record Advancement(
            UUID playerUuid,
            String playerName,
            String advancementKey,
            String locale,
            String title,
            String description
    ) implements DiscordBackendEvent {
        public Advancement(
                UUID playerUuid,
                String playerName,
                String advancementKey,
                String title
        ) {
            this(playerUuid, playerName, advancementKey, "", title, "");
        }
    }
}
