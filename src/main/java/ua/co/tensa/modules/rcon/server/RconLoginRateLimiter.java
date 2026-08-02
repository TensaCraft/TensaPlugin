package ua.co.tensa.modules.rcon.server;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

final class RconLoginRateLimiter {
    enum Decision { ALLOWED, RATE_LIMITED, BLOCKED }

    private record State(int failures, long lastAttemptMillis) {
    }

    private final long rateLimitMillis;
    private final long blockMillis;
    private final int maxFailures;
    private final int capacity;
    private final LinkedHashMap<String, State> states = new LinkedHashMap<>(16, 0.75f, true);

    RconLoginRateLimiter(long rateLimitMillis, long blockMillis, int maxFailures, int capacity) {
        this.rateLimitMillis = rateLimitMillis;
        this.blockMillis = blockMillis;
        this.maxFailures = maxFailures;
        this.capacity = capacity;
    }

    synchronized Decision check(String address, long now) {
        State state = states.get(address);
        if (state == null) {
            return Decision.ALLOWED;
        }
        long elapsed = Math.max(0L, now - state.lastAttemptMillis());
        if (state.failures() >= maxFailures) {
            if (elapsed < blockMillis) {
                return Decision.BLOCKED;
            }
            states.remove(address);
            return Decision.ALLOWED;
        }
        return elapsed < rateLimitMillis ? Decision.RATE_LIMITED : Decision.ALLOWED;
    }

    synchronized int failure(String address, long now) {
        State previous = states.get(address);
        int failures = previous == null ? 1 : previous.failures() + 1;
        states.put(address, new State(failures, now));
        evictOverflow();
        return failures;
    }

    synchronized void success(String address) {
        states.remove(address);
    }

    synchronized int size() {
        return states.size();
    }

    private void evictOverflow() {
        Iterator<Map.Entry<String, State>> iterator = states.entrySet().iterator();
        while (states.size() > capacity && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }
}
