package ua.co.tensa.authbridge.protocol;

import org.junit.jupiter.api.Test;
import ua.co.tensa.authbridge.protocol.security.HmacSha256Authenticator;

import java.util.Arrays;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthProtocolCodecTest {
    private final AuthProtocolCodec codec = new AuthProtocolCodec();

    @Test
    void encodingMatchesCanonicalTensaProxyGoldenVectorByteForByte() throws Exception {
        try (var authenticator = new HmacSha256Authenticator(CanonicalAuthFrameFixture.key(), codec)) {
            AuthFrame signed = authenticator.sign(CanonicalAuthFrameFixture.unsignedState());
            byte[] encoded = codec.encode(signed);

            assertThat(HexFormat.of().formatHex(encoded))
                    .isEqualTo(CanonicalAuthFrameFixture.expectedHex());
            assertThat(encoded).isEqualTo(CanonicalAuthFrameFixture.expectedBytes());
            AuthFrame decoded = codec.decode(encoded);
            assertThat(decoded.majorVersion()).isEqualTo(signed.majorVersion());
            assertThat(decoded.minorVersion()).isEqualTo(signed.minorVersion());
            assertThat(decoded.messageType()).isEqualTo(signed.messageType());
            assertThat(decoded.messageId()).isEqualTo(signed.messageId());
            assertThat(decoded.playerId()).isEqualTo(signed.playerId());
            assertThat(decoded.sessionId()).isEqualTo(signed.sessionId());
            assertThat(decoded.sequence()).isEqualTo(signed.sequence());
            assertThat(decoded.issuedAtEpochMillis()).isEqualTo(signed.issuedAtEpochMillis());
            assertThat(decoded.expiresAtEpochMillis()).isEqualTo(signed.expiresAtEpochMillis());
            assertThat(decoded.nonce()).isEqualTo(signed.nonce());
            assertThat(decoded.challenge()).isEqualTo(signed.challenge());
            assertThat(decoded.backendId()).isEqualTo(signed.backendId());
            assertThat(decoded.authState()).isEqualTo(signed.authState());
            assertThat(decoded.reason()).isEqualTo(signed.reason());
            assertThat(decoded.signature()).isEqualTo(signed.signature());
        }
    }

    @Test
    void signatureUsesCanonicalUnsignedBytesAndLengthPrefixedWireField() {
        try (var authenticator = new HmacSha256Authenticator(CanonicalAuthFrameFixture.key(), codec)) {
            byte[] encoded = codec.encode(authenticator.sign(CanonicalAuthFrameFixture.unsignedState()));

            assertThat(encoded[encoded.length - ProtocolConstants.HMAC_BYTES - 1])
                    .isEqualTo((byte) ProtocolConstants.HMAC_BYTES);
            assertThat(codec.encodeUnsigned(CanonicalAuthFrameFixture.unsignedState()))
                    .isEqualTo(Arrays.copyOf(encoded, encoded.length - ProtocolConstants.HMAC_BYTES - 1));
        }
    }

    @Test
    void rejectsMalformedTruncatedAndOversizedFrames() {
        byte[] valid;
        try (var authenticator = new HmacSha256Authenticator(CanonicalAuthFrameFixture.key(), codec)) {
            valid = codec.encode(authenticator.sign(CanonicalAuthFrameFixture.unsignedState()));
        }
        byte[] wrongMagic = valid.clone();
        wrongMagic[0] ^= 0x01;
        byte[] trailing = Arrays.copyOf(valid, valid.length + 1);

        assertThatThrownBy(() -> codec.decode(wrongMagic)).isInstanceOf(AuthProtocolException.class);
        assertThatThrownBy(() -> codec.decode(Arrays.copyOf(valid, valid.length - 1)))
                .isInstanceOf(AuthProtocolException.class);
        assertThatThrownBy(() -> codec.decode(trailing)).isInstanceOf(AuthProtocolException.class);
        assertThatThrownBy(() -> codec.decode(new byte[ProtocolConstants.MAX_FRAME_BYTES + 1]))
                .isInstanceOf(AuthProtocolException.class);
    }
}
