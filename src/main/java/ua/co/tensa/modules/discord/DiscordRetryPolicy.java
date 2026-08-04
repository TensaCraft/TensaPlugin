package ua.co.tensa.modules.discord;

import java.util.function.LongUnaryOperator;

final class DiscordRetryPolicy {
    private DiscordRetryPolicy() { }

    static long delayMillis(int failedAttempt, LongUnaryOperator randomBelow) {
        if (failedAttempt < 1) {
            throw new IllegalArgumentException("failedAttempt must be positive");
        }
        long base = Math.min(2_000L, 250L << Math.min(3, failedAttempt - 1));
        long jitterBound = Math.max(1L, base / 4L + 1L);
        long jitter = randomBelow.applyAsLong(jitterBound);
        if (jitter < 0L || jitter >= jitterBound) {
            throw new IllegalArgumentException("random jitter is outside its bound");
        }
        return base + jitter;
    }
}
