package ua.co.tensa.authbridge.protocol;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.UUID;

/**
 * Stable, loader-neutral wire format for the Tensa authentication bridge.
 *
 * <p>All numeric fields are big-endian. The final 32 bytes are an HMAC-SHA256
 * over every preceding byte.</p>
 */
public final class AuthBridgeProtocol {
    public static final int VERSION = 1;
    public static final String CHANNEL = "tensa:auth/v1";
    public static final int MAC_LENGTH = 32;
    public static final int MIN_SECRET_LENGTH = 32;
    public static final int MAX_CHALLENGE_BYTES = 512;
    public static final int MAX_PAYLOAD_BYTES = 2048;
    public static final UUID ZERO_SESSION = new UUID(0L, 0L);

    private AuthBridgeProtocol() {
    }

    public static byte[] encode(AuthBridgeMessage message, byte[] secret) throws AuthBridgeProtocolException {
        validateSecret(secret);
        validateForEncoding(message);

        try {
            ByteArrayOutputStream bodyBuffer = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bodyBuffer)) {
                byte[] challenge = message.backendChallenge().getBytes(StandardCharsets.UTF_8);
                output.writeInt(message.version());
                output.writeByte(message.type().wireId());
                writeUuid(output, message.playerId());
                writeUuid(output, message.sessionId());
                output.writeShort(challenge.length);
                output.write(challenge);
                output.writeLong(message.sequence());
                output.writeLong(message.issuedAt());
                output.writeLong(message.expiresAt());
                output.writeByte(message.state().wireId());
            }

            byte[] body = bodyBuffer.toByteArray();
            byte[] mac = hmac(body, secret);
            byte[] payload = Arrays.copyOf(body, body.length + mac.length);
            System.arraycopy(mac, 0, payload, body.length, mac.length);
            if (payload.length > MAX_PAYLOAD_BYTES) {
                throw new AuthBridgeProtocolException("Encoded payload exceeds " + MAX_PAYLOAD_BYTES + " bytes");
            }
            return payload;
        } catch (IOException e) {
            throw new AuthBridgeProtocolException("Unable to encode authentication bridge message", e);
        }
    }

    public static AuthBridgeMessage decodeAndVerify(byte[] payload, byte[] secret) throws AuthBridgeProtocolException {
        validateSecret(secret);
        if (payload == null || payload.length <= MAC_LENGTH || payload.length > MAX_PAYLOAD_BYTES) {
            throw new AuthBridgeProtocolException("Invalid payload length");
        }

        int bodyLength = payload.length - MAC_LENGTH;
        byte[] body = Arrays.copyOf(payload, bodyLength);
        byte[] providedMac = Arrays.copyOfRange(payload, bodyLength, payload.length);
        byte[] expectedMac = hmac(body, secret);
        if (!MessageDigest.isEqual(expectedMac, providedMac)) {
            throw new AuthBridgeProtocolException("Invalid message authentication code");
        }

        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(body))) {
            int version = input.readInt();
            if (version != VERSION) {
                throw new AuthBridgeProtocolException("Unsupported protocol version: " + version);
            }
            AuthBridgeMessageType type = AuthBridgeMessageType.fromWireId(input.readUnsignedByte());
            UUID playerId = readUuid(input);
            UUID sessionId = readUuid(input);
            int challengeLength = input.readUnsignedShort();
            if (challengeLength > MAX_CHALLENGE_BYTES || challengeLength > input.available()) {
                throw new AuthBridgeProtocolException("Invalid backend challenge length");
            }
            String challenge = new String(input.readNBytes(challengeLength), StandardCharsets.UTF_8);
            long sequence = input.readLong();
            long issuedAt = input.readLong();
            long expiresAt = input.readLong();
            AuthBridgeState state = AuthBridgeState.fromWireId(input.readUnsignedByte());
            if (input.available() != 0) {
                throw new AuthBridgeProtocolException("Unexpected trailing message data");
            }
            return new AuthBridgeMessage(
                    version,
                    type,
                    playerId,
                    sessionId,
                    challenge,
                    sequence,
                    issuedAt,
                    expiresAt,
                    state
            );
        } catch (EOFException e) {
            throw new AuthBridgeProtocolException("Truncated authentication bridge message", e);
        } catch (IOException e) {
            throw new AuthBridgeProtocolException("Unable to decode authentication bridge message", e);
        }
    }

    private static void validateForEncoding(AuthBridgeMessage message) throws AuthBridgeProtocolException {
        if (message.version() != VERSION) {
            throw new AuthBridgeProtocolException("Unsupported protocol version: " + message.version());
        }
        int challengeBytes = message.backendChallenge().getBytes(StandardCharsets.UTF_8).length;
        if (challengeBytes > MAX_CHALLENGE_BYTES) {
            throw new AuthBridgeProtocolException("Backend challenge exceeds " + MAX_CHALLENGE_BYTES + " bytes");
        }
    }

    private static void validateSecret(byte[] secret) throws AuthBridgeProtocolException {
        if (secret == null || secret.length < MIN_SECRET_LENGTH) {
            throw new AuthBridgeProtocolException("Bridge secret must contain at least " + MIN_SECRET_LENGTH + " bytes");
        }
    }

    private static byte[] hmac(byte[] body, byte[] secret) throws AuthBridgeProtocolException {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(body);
        } catch (GeneralSecurityException e) {
            throw new AuthBridgeProtocolException("HMAC-SHA256 is unavailable", e);
        }
    }

    private static void writeUuid(DataOutputStream output, UUID uuid) throws IOException {
        output.writeLong(uuid.getMostSignificantBits());
        output.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }
}
