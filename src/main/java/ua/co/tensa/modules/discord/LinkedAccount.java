package ua.co.tensa.modules.discord;

import java.time.Instant;
import java.util.UUID;

public record LinkedAccount(
        UUID playerUuid,
        String playerName,
        String discordUserId,
        String discordUserName,
        Instant linkedAt
) {
}
