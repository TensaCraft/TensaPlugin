package ua.co.tensa.authbridge.protocol;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public record AuthFrame(
        int majorVersion,
        int minorVersion,
        AuthMessageType messageType,
        UUID messageId,
        UUID playerId,
        UUID sessionId,
        long sequence,
        long issuedAtEpochMillis,
        long expiresAtEpochMillis,
        byte[] nonce,
        byte[] challenge,
        String backendId,
        AuthState authState,
        String reason,
        byte[] signature
) {
    public AuthFrame {
        if (majorVersion < 0 || majorVersion > 255 || minorVersion < 0 || minorVersion > 255) {
            throw new IllegalArgumentException("Protocol versions must fit an unsigned byte");
        }
        messageType = Objects.requireNonNull(messageType, "messageType");
        messageId = Objects.requireNonNull(messageId, "messageId");
        playerId = Objects.requireNonNull(playerId, "playerId");
        sessionId = Objects.requireNonNull(sessionId, "sessionId");
        nonce = copyExact(nonce, ProtocolConstants.NONCE_BYTES, "nonce");
        challenge = copyExact(challenge, ProtocolConstants.CHALLENGE_BYTES, "challenge");
        backendId = requireText(backendId, "backendId");
        authState = Objects.requireNonNull(authState, "authState");
        reason = Objects.requireNonNullElse(reason, "");
        signature = signature == null ? new byte[0] : signature.clone();
        if (signature.length != 0 && signature.length != ProtocolConstants.HMAC_BYTES) {
            throw new IllegalArgumentException("signature must be empty or 32 bytes");
        }
    }

    @Override
    public byte[] nonce() {
        return nonce.clone();
    }

    @Override
    public byte[] challenge() {
        return challenge.clone();
    }

    @Override
    public byte[] signature() {
        return signature.clone();
    }

    public AuthFrame withSignature(byte[] newSignature) {
        return new AuthFrame(
                majorVersion,
                minorVersion,
                messageType,
                messageId,
                playerId,
                sessionId,
                sequence,
                issuedAtEpochMillis,
                expiresAtEpochMillis,
                nonce,
                challenge,
                backendId,
                authState,
                reason,
                newSignature
        );
    }

    public AuthFrame withoutSignature() {
        return withSignature(new byte[0]);
    }

    public boolean hasSignature() {
        return signature.length == ProtocolConstants.HMAC_BYTES;
    }

    private static byte[] copyExact(byte[] value, int expectedLength, String field) {
        Objects.requireNonNull(value, field);
        if (value.length != expectedLength) {
            throw new IllegalArgumentException(field + " must be exactly " + expectedLength + " bytes");
        }
        return Arrays.copyOf(value, value.length);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
