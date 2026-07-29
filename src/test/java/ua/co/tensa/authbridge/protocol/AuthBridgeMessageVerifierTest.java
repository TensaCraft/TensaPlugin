package ua.co.tensa.authbridge.protocol;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthBridgeMessageVerifierTest {
    private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");

    @Test
    void acceptsFreshIncreasingSequencesAndRejectsReplay() {
        AuthBridgeMessageVerifier verifier = verifier();

        assertThatCode(() -> verifier.verifyFresh("aero", message(1L, -1_000L, 5_000L)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> verifier.verifyFresh("aero", message(1L, -1_000L, 5_000L)))
                .isInstanceOf(AuthBridgeProtocolException.class)
                .hasMessageContaining("Replayed");
        assertThatCode(() -> verifier.verifyFresh("aero", message(2L, -500L, 5_000L)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsExpiredFutureAndOverlongMessages() {
        AuthBridgeMessageVerifier expiredVerifier = verifier();
        assertThatThrownBy(() -> expiredVerifier.verifyFresh("aero", message(1L, -10_000L, -5_000L)))
                .isInstanceOf(AuthBridgeProtocolException.class)
                .hasMessageContaining("expired");

        AuthBridgeMessageVerifier futureVerifier = verifier();
        assertThatThrownBy(() -> futureVerifier.verifyFresh("aero", message(1L, 5_000L, 10_000L)))
                .isInstanceOf(AuthBridgeProtocolException.class)
                .hasMessageContaining("future");

        AuthBridgeMessageVerifier ttlVerifier = verifier();
        assertThatThrownBy(() -> ttlVerifier.verifyFresh("aero", message(1L, -1_000L, 20_000L)))
                .isInstanceOf(AuthBridgeProtocolException.class)
                .hasMessageContaining("TTL");
    }

    private AuthBridgeMessageVerifier verifier() {
        return new AuthBridgeMessageVerifier(
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofSeconds(10),
                Duration.ofSeconds(2),
                new AuthBridgeReplayGuard()
        );
    }

    private AuthBridgeMessage message(long sequence, long issuedOffset, long expiresOffset) {
        return new AuthBridgeMessage(
                AuthBridgeProtocol.VERSION,
                AuthBridgeMessageType.QUERY,
                UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"),
                UUID.fromString("10213243-5465-7687-98a9-bacbdcedfe0f"),
                "challenge-123456",
                sequence,
                NOW.toEpochMilli() + issuedOffset,
                NOW.toEpochMilli() + expiresOffset,
                AuthBridgeState.LOCKED
        );
    }
}
