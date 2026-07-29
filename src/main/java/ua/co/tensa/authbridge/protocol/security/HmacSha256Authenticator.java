package ua.co.tensa.authbridge.protocol.security;

import ua.co.tensa.authbridge.protocol.AuthFrame;
import ua.co.tensa.authbridge.protocol.AuthProtocolCodec;
import ua.co.tensa.authbridge.protocol.ProtocolConstants;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Objects;

public final class HmacSha256Authenticator implements AutoCloseable {
    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] key;
    private final AuthProtocolCodec codec;

    public HmacSha256Authenticator(byte[] key, AuthProtocolCodec codec) {
        Objects.requireNonNull(key, "key");
        if (key.length < ProtocolConstants.HMAC_BYTES) {
            throw new IllegalArgumentException("The auth key must contain at least 256 bits");
        }
        this.key = Arrays.copyOf(key, key.length);
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    public AuthFrame sign(AuthFrame frame) {
        return frame.withSignature(mac(codec.encodeUnsigned(frame)));
    }

    public boolean verify(AuthFrame frame) {
        if (!frame.hasSignature()) {
            return false;
        }
        byte[] expected = mac(codec.encodeUnsigned(frame));
        return MessageDigest.isEqual(expected, frame.signature());
    }

    @Override
    public void close() {
        Arrays.fill(key, (byte) 0);
    }

    private byte[] mac(byte[] payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return mac.doFinal(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }
}
