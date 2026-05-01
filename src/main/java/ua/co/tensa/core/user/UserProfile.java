package ua.co.tensa.core.user;

import java.util.UUID;

public record UserProfile(
        UUID uuid,
        String username,
        String firstUsername,
        long firstSeenAt,
        long lastSeenAt,
        long lastDisconnectAt,
        String lastIp,
        String lastVirtualHost,
        String lastProtocolVersion,
        String lastServer,
        long joinCount,
        long totalPlayTimeSeconds,
        long onlineSince,
        long createdAt,
        long updatedAt
) {
}
