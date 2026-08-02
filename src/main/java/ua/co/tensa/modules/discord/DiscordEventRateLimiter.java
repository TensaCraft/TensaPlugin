package ua.co.tensa.modules.discord;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

final class DiscordEventRateLimiter {
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final int limit;
    private final Deque<Instant> accepted = new ArrayDeque<>();

    DiscordEventRateLimiter(int limit) {
        this.limit = limit;
    }

    synchronized boolean tryAcquire(Instant now) {
        Instant cutoff = now.minus(WINDOW);
        while (!accepted.isEmpty() && !accepted.peekFirst().isAfter(cutoff)) {
            accepted.removeFirst();
        }
        if (accepted.size() >= limit) {
            return false;
        }
        accepted.addLast(now);
        return true;
    }
}
