package ua.co.tensa.modules.discord;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

final class BackendStatusDeduplicator {
    enum Transition {
        UNAVAILABLE,
        RECOVERED
    }

    private final Duration debounce;
    private final int confirmations;
    private final int capacity;
    private final Map<String, State> states = new LinkedHashMap<>(16, 0.75f, true);

    BackendStatusDeduplicator(Duration debounce, int confirmations, int capacity) {
        this.debounce = debounce;
        this.confirmations = confirmations;
        this.capacity = capacity;
    }

    synchronized Optional<Transition> observe(String serverName, boolean available, Instant now) {
        State state = states.get(serverName);
        if (state == null) {
            states.put(serverName, new State(available, available, now, 0));
            trimToCapacity();
            return Optional.empty();
        }
        if (state.stable == available) {
            state.candidate = available;
            state.candidateSince = now;
            state.confirmations = 0;
            return Optional.empty();
        }
        if (state.candidate != available) {
            state.candidate = available;
            state.candidateSince = now;
            state.confirmations = 1;
        } else {
            state.confirmations++;
        }
        if (state.confirmations < confirmations || now.isBefore(state.candidateSince.plus(debounce))) {
            return Optional.empty();
        }
        state.stable = available;
        state.confirmations = 0;
        return Optional.of(available ? Transition.RECOVERED : Transition.UNAVAILABLE);
    }

    synchronized int size() {
        return states.size();
    }

    private void trimToCapacity() {
        while (states.size() > capacity) {
            String eldest = states.keySet().iterator().next();
            states.remove(eldest);
        }
    }

    private static final class State {
        private boolean stable;
        private boolean candidate;
        private Instant candidateSince;
        private int confirmations;

        private State(boolean stable, boolean candidate, Instant candidateSince, int confirmations) {
            this.stable = stable;
            this.candidate = candidate;
            this.candidateSince = candidateSince;
            this.confirmations = confirmations;
        }
    }
}
