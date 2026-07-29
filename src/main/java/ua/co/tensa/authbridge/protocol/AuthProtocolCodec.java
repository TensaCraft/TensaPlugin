package ua.co.tensa.authbridge.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class AuthProtocolCodec {
    public byte[] encode(AuthFrame frame) {
        if (!frame.hasSignature()) {
            throw new AuthProtocolException("A transmitted frame must have a signature");
        }
        return encodeInternal(frame, true);
    }

    public byte[] encodeUnsigned(AuthFrame frame) {
        return encodeInternal(frame, false);
    }

    public AuthFrame decode(byte[] payload) {
        if (payload == null || payload.length == 0 || payload.length > ProtocolConstants.MAX_FRAME_BYTES) {
            throw new AuthProtocolException("Frame size is outside the allowed range");
        }

        try (var input = new DataInputStream(new ByteArrayInputStream(payload))) {
            int magic = input.readInt();
            if (magic != ProtocolConstants.MAGIC) {
                throw new AuthProtocolException("Invalid protocol magic");
            }

            int major = input.readUnsignedByte();
            int minor = input.readUnsignedByte();
            if (major != ProtocolConstants.CURRENT_MAJOR || minor > ProtocolConstants.CURRENT_MINOR) {
                throw new AuthProtocolException("Unsupported protocol version " + major + "." + minor);
            }

            AuthMessageType messageType = AuthMessageType.fromWireId(input.readUnsignedByte());
            AuthState authState = AuthState.fromWireId(input.readUnsignedByte());
            int flags = input.readUnsignedByte();
            if (flags != 0) {
                throw new AuthProtocolException("Unsupported protocol flags");
            }

            UUID messageId = readUuid(input);
            UUID playerId = readUuid(input);
            UUID sessionId = readUuid(input);
            long sequence = input.readLong();
            long issuedAt = input.readLong();
            long expiresAt = input.readLong();
            byte[] nonce = readExactBytes(input, ProtocolConstants.NONCE_BYTES, "nonce");
            byte[] challenge = readExactBytes(input, ProtocolConstants.CHALLENGE_BYTES, "challenge");
            String backendId = readString(input, ProtocolConstants.MAX_BACKEND_ID_BYTES, "backendId");
            String reason = readString(input, ProtocolConstants.MAX_REASON_BYTES, "reason");
            byte[] signature = readExactBytes(input, ProtocolConstants.HMAC_BYTES, "signature");

            if (input.available() != 0) {
                throw new AuthProtocolException("Trailing bytes after auth frame");
            }

            return new AuthFrame(
                    major,
                    minor,
                    messageType,
                    messageId,
                    playerId,
                    sessionId,
                    sequence,
                    issuedAt,
                    expiresAt,
                    nonce,
                    challenge,
                    backendId,
                    authState,
                    reason,
                    signature
            );
        } catch (EOFException exception) {
            throw new AuthProtocolException("Truncated auth frame", exception);
        } catch (IOException exception) {
            throw new AuthProtocolException("Unable to decode auth frame", exception);
        } catch (IllegalArgumentException exception) {
            throw new AuthProtocolException("Invalid auth frame: " + exception.getMessage(), exception);
        }
    }

    private byte[] encodeInternal(AuthFrame frame, boolean includeSignature) {
        try {
            var bytes = new ByteArrayOutputStream(256);
            try (var output = new DataOutputStream(bytes)) {
                output.writeInt(ProtocolConstants.MAGIC);
                output.writeByte(frame.majorVersion());
                output.writeByte(frame.minorVersion());
                output.writeByte(frame.messageType().wireId());
                output.writeByte(frame.authState().wireId());
                output.writeByte(0);
                writeUuid(output, frame.messageId());
                writeUuid(output, frame.playerId());
                writeUuid(output, frame.sessionId());
                output.writeLong(frame.sequence());
                output.writeLong(frame.issuedAtEpochMillis());
                output.writeLong(frame.expiresAtEpochMillis());
                writeBytes(output, frame.nonce());
                writeBytes(output, frame.challenge());
                writeString(output, frame.backendId(), ProtocolConstants.MAX_BACKEND_ID_BYTES, "backendId");
                writeString(output, frame.reason(), ProtocolConstants.MAX_REASON_BYTES, "reason");
                if (includeSignature) {
                    writeBytes(output, frame.signature());
                }
            }
            byte[] encoded = bytes.toByteArray();
            if (encoded.length > ProtocolConstants.MAX_FRAME_BYTES) {
                throw new AuthProtocolException("Encoded frame exceeds maximum size");
            }
            return encoded;
        } catch (IOException exception) {
            throw new AuthProtocolException("Unable to encode auth frame", exception);
        }
    }

    private static void writeUuid(DataOutputStream output, UUID value) throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    private static void writeBytes(DataOutputStream output, byte[] value) throws IOException {
        if (value.length > 255) {
            throw new AuthProtocolException("Binary field is too large");
        }
        output.writeByte(value.length);
        output.write(value);
    }

    private static byte[] readExactBytes(DataInputStream input, int expectedLength, String field) throws IOException {
        int length = input.readUnsignedByte();
        if (length != expectedLength) {
            throw new AuthProtocolException(field + " must be " + expectedLength + " bytes");
        }
        byte[] value = input.readNBytes(length);
        if (value.length != length) {
            throw new EOFException("Truncated " + field);
        }
        return value;
    }

    private static void writeString(
            DataOutputStream output,
            String value,
            int maximumBytes,
            String field
    ) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > maximumBytes) {
            throw new AuthProtocolException(field + " exceeds " + maximumBytes + " bytes");
        }
        output.writeShort(encoded.length);
        output.write(encoded);
    }

    private static String readString(DataInputStream input, int maximumBytes, String field) throws IOException {
        int length = input.readUnsignedShort();
        if (length > maximumBytes) {
            throw new AuthProtocolException(field + " exceeds " + maximumBytes + " bytes");
        }
        byte[] encoded = input.readNBytes(length);
        if (encoded.length != length) {
            throw new EOFException("Truncated " + field);
        }
        return new String(encoded, StandardCharsets.UTF_8);
    }
}
