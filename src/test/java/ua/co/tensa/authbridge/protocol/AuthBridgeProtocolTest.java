package ua.co.tensa.authbridge.protocol;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthBridgeProtocolTest {
    private static final byte[] SECRET =
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    @Test
    void encodingMatchesStableGoldenVector() throws Exception {
        AuthBridgeMessage message = new AuthBridgeMessage(
                AuthBridgeProtocol.VERSION,
                AuthBridgeMessageType.STATE,
                UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"),
                UUID.fromString("10213243-5465-7687-98a9-bacbdcedfe0f"),
                "backend-01",
                42L,
                1_700_000_000_000L,
                1_700_000_010_000L,
                AuthBridgeState.AUTHORIZED
        );

        byte[] payload = AuthBridgeProtocol.encode(message, SECRET);

        assertThat(HexFormat.of().formatHex(payload)).isEqualTo(
                "000000010200112233445566778899aabbccddeeff"
                        + "102132435465768798a9bacbdcedfe0f000a6261636b656e642d3031"
                        + "000000000000002a0000018bcfe568000000018bcfe58f1001"
                        + "2d5edf50b5591145552123a42415958be101c7f3d530f450c8dbb5381a4a06a5"
        );
        assertThat(AuthBridgeProtocol.decodeAndVerify(payload, SECRET)).isEqualTo(message);
    }

    @Test
    void tamperedPayloadFailsMacVerification() throws Exception {
        AuthBridgeMessage message = message(1L);
        byte[] payload = AuthBridgeProtocol.encode(message, SECRET);
        payload[12] ^= 0x01;

        assertThatThrownBy(() -> AuthBridgeProtocol.decodeAndVerify(payload, SECRET))
                .isInstanceOf(AuthBridgeProtocolException.class)
                .hasMessageContaining("authentication code");
    }

    @Test
    void shortSecretsAreRejected() {
        assertThatThrownBy(() -> AuthBridgeProtocol.encode(message(1L), new byte[16]))
                .isInstanceOf(AuthBridgeProtocolException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    private AuthBridgeMessage message(long sequence) {
        return new AuthBridgeMessage(
                AuthBridgeProtocol.VERSION,
                AuthBridgeMessageType.QUERY,
                UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"),
                AuthBridgeProtocol.ZERO_SESSION,
                "challenge-123456",
                sequence,
                1_700_000_000_000L,
                1_700_000_010_000L,
                AuthBridgeState.LOCKED
        );
    }
}
