package ua.co.tensa.modules.discord;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

final class RecentEventDeduplicator {
    private final Duration window;
    private final int capacity;
    private final Map<String, Instant> seen = new LinkedHashMap<>(16, 0.75f, true);

    RecentEventDeduplicator(Duration window, int capacity) {
        this.window = window;
        this.capacity = capacity;
    }

    synchronized boolean firstOccurrence(String key, Instant now) {
        purgeExpired(now);
        Instant previous = seen.put(key, now);
        while (seen.size() > capacity) {
            seen.remove(seen.keySet().iterator().next());
        }
        return previous == null || !previous.plus(window).isAfter(now);
    }

    synchronized int size() {
        return seen.size();
    }

    private void purgeExpired(Instant now) {
        Iterator<Map.Entry<String, Instant>> iterator = seen.entrySet().iterator();
        while (iterator.hasNext()) {
            if (!iterator.next().getValue().plus(window).isAfter(now)) {
                iterator.remove();
            }
        }
    }
}
