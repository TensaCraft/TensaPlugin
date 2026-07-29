package ua.co.tensa.authbridge.protocol;

import java.util.Objects;
import java.util.UUID;

public record AuthBridgeMessage(
        int version,
        AuthBridgeMessageType type,
        UUID playerId,
        UUID sessionId,
        String backendChallenge,
        long sequence,
        long issuedAt,
        long expiresAt,
        AuthBridgeState state
) {
    public AuthBridgeMessage {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(backendChallenge, "backendChallenge");
        Objects.requireNonNull(state, "state");
    }
}
