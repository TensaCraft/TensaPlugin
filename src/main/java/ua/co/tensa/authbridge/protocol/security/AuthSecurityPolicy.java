package ua.co.tensa.authbridge.protocol.security;

import java.time.Duration;
import java.util.Objects;

public record AuthSecurityPolicy(
        Duration maximumClockSkew,
        Duration maximumFrameTtl,
        int replayCapacity
) {
    public AuthSecurityPolicy {
        maximumClockSkew = requirePositive(maximumClockSkew, "maximumClockSkew");
        maximumFrameTtl = requirePositive(maximumFrameTtl, "maximumFrameTtl");
        if (replayCapacity < 128) {
            throw new IllegalArgumentException("replayCapacity must be at least 128");
        }
    }

    private static Duration requirePositive(Duration value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }
}
