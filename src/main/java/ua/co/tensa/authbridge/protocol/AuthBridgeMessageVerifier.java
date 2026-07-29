package ua.co.tensa.authbridge.protocol;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

public final class AuthBridgeMessageVerifier {
    private final Clock clock;
    private final long maxTtlMillis;
    private final long clockSkewMillis;
    private final AuthBridgeReplayGuard replayGuard;

    public AuthBridgeMessageVerifier(
            Clock clock,
            Duration maxTtl,
            Duration clockSkew,
            AuthBridgeReplayGuard replayGuard
    ) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.maxTtlMillis = positiveMillis(maxTtl, "maxTtl");
        this.clockSkewMillis = nonNegativeMillis(clockSkew, "clockSkew");
        this.replayGuard = Objects.requireNonNull(replayGuard, "replayGuard");
    }

    public void verifyFresh(String peer, AuthBridgeMessage message) throws AuthBridgeProtocolException {
        long now = clock.millis();
        if (message.sequence() <= 0L) {
            throw new AuthBridgeProtocolException("Sequence must be positive");
        }
        if (message.expiresAt() <= message.issuedAt()) {
            throw new AuthBridgeProtocolException("Message expiry must be after issue time");
        }
        long ttl;
        try {
            ttl = Math.subtractExact(message.expiresAt(), message.issuedAt());
        } catch (ArithmeticException e) {
            throw new AuthBridgeProtocolException("Message TTL overflow", e);
        }
        if (ttl > maxTtlMillis) {
            throw new AuthBridgeProtocolException("Message TTL exceeds the configured maximum");
        }
        if (message.issuedAt() > saturatedAdd(now, clockSkewMillis)) {
            throw new AuthBridgeProtocolException("Message issue time is in the future");
        }
        if (message.expiresAt() < saturatedSubtract(now, clockSkewMillis)) {
            throw new AuthBridgeProtocolException("Message has expired");
        }
        if (!replayGuard.accept(peer, message, now)) {
            throw new AuthBridgeProtocolException("Replayed or out-of-order message");
        }
    }

    private static long positiveMillis(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        long millis = duration.toMillis();
        if (millis <= 0L) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return millis;
    }

    private static long nonNegativeMillis(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        long millis = duration.toMillis();
        if (millis < 0L) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return millis;
    }

    private static long saturatedAdd(long value, long addend) {
        if (addend > 0L && value > Long.MAX_VALUE - addend) {
            return Long.MAX_VALUE;
        }
        return value + addend;
    }

    private static long saturatedSubtract(long value, long subtrahend) {
        if (subtrahend > 0L && value < Long.MIN_VALUE + subtrahend) {
            return Long.MIN_VALUE;
        }
        return value - subtrahend;
    }
}
