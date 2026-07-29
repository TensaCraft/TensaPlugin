package ua.co.tensa.authbridge.protocol;

import org.junit.jupiter.api.Test;
import ua.co.tensa.authbridge.protocol.security.AuthFrameVerifier;
import ua.co.tensa.authbridge.protocol.security.AuthSecurityPolicy;
import ua.co.tensa.authbridge.protocol.security.HmacSha256Authenticator;
import ua.co.tensa.authbridge.protocol.security.ReplayWindow;
import ua.co.tensa.authbridge.protocol.security.VerificationFailure;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthSecurityTest {
    private final AuthProtocolCodec codec = new AuthProtocolCodec();
    private final AuthSecurityPolicy policy =
            new AuthSecurityPolicy(Duration.ofSeconds(5), Duration.ofSeconds(15), 128);

    @Test
    void verifiesHmacInConstantTimeAndRejectsWrongBackend() {
        try (var authenticator = new HmacSha256Authenticator(CanonicalAuthFrameFixture.key(), codec)) {
            AuthFrame signed = authenticator.sign(CanonicalAuthFrameFixture.unsignedState());
            AuthFrame tampered = signed.withSignature(tampered(signed.signature()));
            AuthFrameVerifier verifier = verifier(authenticator);

            assertThat(verifier.verify(signed, CanonicalAuthFrameFixture.ISSUED_AT).accepted()).isTrue();
            assertThat(verifier(authenticator).verify(tampered, CanonicalAuthFrameFixture.ISSUED_AT).failure())
                    .isEqualTo(VerificationFailure.INVALID_SIGNATURE);
            assertThat(verifier(authenticator, "other").verify(signed, CanonicalAuthFrameFixture.ISSUED_AT).failure())
                    .isEqualTo(VerificationFailure.WRONG_BACKEND);
        }
    }

    @Test
    void rejectsExpiredFutureOverlongAndReplayFrames() {
        try (var authenticator = new HmacSha256Authenticator(CanonicalAuthFrameFixture.key(), codec)) {
            long now = CanonicalAuthFrameFixture.ISSUED_AT;
            AuthFrame expired = authenticator.sign(challenge(
                    UUID.randomUUID(),
                    CanonicalAuthFrameFixture.bytes(24, 80),
                    now - 10_000,
                    now
            ));
            AuthFrame future = authenticator.sign(challenge(
                    UUID.randomUUID(),
                    CanonicalAuthFrameFixture.bytes(24, 110),
                    now + 6_000,
                    now + 12_000
            ));
            AuthFrame overlong = authenticator.sign(challenge(
                    UUID.randomUUID(),
                    CanonicalAuthFrameFixture.bytes(24, 140),
                    now,
                    now + 16_000
            ));

            assertThat(verifier(authenticator).verify(expired, now).failure())
                    .isEqualTo(VerificationFailure.EXPIRED);
            assertThat(verifier(authenticator).verify(future, now).failure())
                    .isEqualTo(VerificationFailure.NOT_YET_VALID);
            assertThat(verifier(authenticator).verify(overlong, now).failure())
                    .isEqualTo(VerificationFailure.INVALID_TIME_WINDOW);

            AuthFrameVerifier replayVerifier = verifier(authenticator);
            AuthFrame fresh = authenticator.sign(state(
                    UUID.randomUUID(),
                    1L,
                    CanonicalAuthFrameFixture.bytes(24, 170),
                    now,
                    now + 10_000
            ));
            AuthFrame sameNonce = authenticator.sign(state(
                    UUID.randomUUID(),
                    2L,
                    fresh.nonce(),
                    now,
                    now + 10_000
            ));
            AuthFrame staleSequence = authenticator.sign(state(
                    UUID.randomUUID(),
                    1L,
                    CanonicalAuthFrameFixture.bytes(24, 200),
                    now,
                    now + 10_000
            ));
            assertThat(replayVerifier.verify(fresh, now).accepted()).isTrue();
            assertThat(replayVerifier.verify(fresh, now).failure())
                    .isEqualTo(VerificationFailure.REPLAYED_MESSAGE);
            assertThat(replayVerifier.verify(sameNonce, now).failure())
                    .isEqualTo(VerificationFailure.REPLAYED_NONCE);
            assertThat(replayVerifier.verify(staleSequence, now).failure())
                    .isEqualTo(VerificationFailure.STALE_SEQUENCE);
        }
    }

    private AuthFrame challenge(UUID messageId, byte[] nonce, long issuedAt, long expiresAt) {
        return CanonicalAuthFrameFixture.unsignedChallenge(messageId, nonce, issuedAt, expiresAt);
    }

    private AuthFrame state(
            UUID messageId,
            long sequence,
            byte[] nonce,
            long issuedAt,
            long expiresAt
    ) {
        AuthFrame fixture = CanonicalAuthFrameFixture.unsignedState();
        return new AuthFrame(
                fixture.majorVersion(),
                fixture.minorVersion(),
                fixture.messageType(),
                messageId,
                fixture.playerId(),
                fixture.sessionId(),
                sequence,
                issuedAt,
                expiresAt,
                nonce,
                fixture.challenge(),
                fixture.backendId(),
                fixture.authState(),
                fixture.reason(),
                new byte[0]
        );
    }

    private AuthFrameVerifier verifier(HmacSha256Authenticator authenticator) {
        return verifier(authenticator, CanonicalAuthFrameFixture.BACKEND_ID);
    }

    private AuthFrameVerifier verifier(HmacSha256Authenticator authenticator, String backendId) {
        return new AuthFrameVerifier(backendId, policy, authenticator, new ReplayWindow(128));
    }

    private byte[] tampered(byte[] signature) {
        byte[] tampered = signature.clone();
        tampered[0] ^= 0x01;
        return tampered;
    }
}
