package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerPresenceTrackerTest {
    @TempDir
    Path tempDir;

    @Test
    void suppressesAuthHopAndPublishesJoinSwitchQuitForSelectedBackends() {
        PlayerPresenceTracker tracker = tracker(8);
        UUID player = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        assertThat(tracker.connected(player, "aero-auth")).isEmpty();
        assertThat(tracker.connected(player, "aero"))
                .contains(new PlayerPresenceTracker.Transition(PlayerPresenceTracker.Type.JOIN, "", "aero"));
        assertThat(tracker.connected(player, "aero")).isEmpty();
        assertThat(tracker.connected(player, "aero-lobby"))
                .contains(new PlayerPresenceTracker.Transition(PlayerPresenceTracker.Type.SWITCH, "aero", "aero-lobby"));
        assertThat(tracker.disconnected(player))
                .contains(new PlayerPresenceTracker.Transition(PlayerPresenceTracker.Type.QUIT, "aero-lobby", ""));
        assertThat(tracker.disconnected(player)).isEmpty();
    }

    @Test
    void boundsPresenceStateAndCanPrimeWithoutCreatingJoin() {
        PlayerPresenceTracker tracker = tracker(2);
        UUID first = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID second = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID third = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

        tracker.prime(first, "aero");
        tracker.connected(second, "aero");
        tracker.connected(third, "aero");

        assertThat(tracker.size()).isEqualTo(2);
        assertThat(tracker.connected(first, "aero"))
                .map(PlayerPresenceTracker.Transition::type)
                .isEqualTo(Optional.of(PlayerPresenceTracker.Type.JOIN));
    }

    private PlayerPresenceTracker tracker(int capacity) {
        return new PlayerPresenceTracker(
                new DiscordServerPolicy(DiscordTestSettings.create(tempDir)),
                capacity
        );
    }
}
