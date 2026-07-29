package ua.co.tensa.authbridge.protocol;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

final class CanonicalAuthFrameFixture {
    static final UUID MESSAGE_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    static final UUID PLAYER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    static final UUID SESSION_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    static final long ISSUED_AT = 1_800_000_000_000L;
    static final long EXPIRES_AT = 1_800_000_010_000L;
    static final String BACKEND_ID = "aero-test";

    private CanonicalAuthFrameFixture() {
    }

    static byte[] key() {
        return bytes(ProtocolConstants.HMAC_BYTES, 1);
    }

    static byte[] nonce() {
        return bytes(ProtocolConstants.NONCE_BYTES, 10);
    }

    static byte[] challenge() {
        return bytes(ProtocolConstants.CHALLENGE_BYTES, 40);
    }

    static AuthFrame unsignedState() {
        return frame(
                AuthMessageType.AUTH_STATE,
                AuthState.AUTHORIZED,
                MESSAGE_ID,
                7L,
                ISSUED_AT,
                EXPIRES_AT,
                nonce(),
                "authenticated"
        );
    }

    static AuthFrame unsignedChallenge(UUID messageId, byte[] nonce, long issuedAt, long expiresAt) {
        return frame(
                AuthMessageType.CHALLENGE,
                AuthState.PENDING,
                messageId,
                0L,
                issuedAt,
                expiresAt,
                nonce,
                ""
        );
    }

    static String expectedHex() throws IOException {
        try (var stream = CanonicalAuthFrameFixture.class.getResourceAsStream(
                "/authbridge/golden-auth-state-v1.hex"
        )) {
            if (stream == null) {
                throw new IOException("Missing canonical auth bridge golden fixture");
            }
            return new String(stream.readAllBytes(), StandardCharsets.US_ASCII).trim();
        }
    }

    static byte[] expectedBytes() throws IOException {
        return HexFormat.of().parseHex(expectedHex());
    }

    static byte[] bytes(int length, int seed) {
        byte[] value = new byte[length];
        for (int index = 0; index < value.length; index++) {
            value[index] = (byte) (seed + index);
        }
        return value;
    }

    private static AuthFrame frame(
            AuthMessageType type,
            AuthState state,
            UUID messageId,
            long sequence,
            long issuedAt,
            long expiresAt,
            byte[] nonce,
            String reason
    ) {
        return new AuthFrame(
                ProtocolConstants.CURRENT_MAJOR,
                ProtocolConstants.CURRENT_MINOR,
                type,
                messageId,
                PLAYER_ID,
                SESSION_ID,
                sequence,
                issuedAt,
                expiresAt,
                nonce,
                challenge(),
                BACKEND_ID,
                state,
                reason,
                new byte[0]
        );
    }
}
