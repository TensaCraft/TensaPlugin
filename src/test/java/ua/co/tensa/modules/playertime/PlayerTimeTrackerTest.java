package ua.co.tensa.modules.playertime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.co.tensa.core.user.UserDataService;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerTimeTrackerTest {
    @TempDir Path temporary;

    @Test
    void immediatelyRejectedLookupCompletesAndDoesNotPoisonSingleFlightCache() {
        UserDataService service = UserDataService.local(temporary.resolve("users"), "test_");
        service.close();
        PlayerTimeTracker tracker = new PlayerTimeTracker(service);
        UUID player = UUID.randomUUID();

        CompletableFuture<Long> first = tracker.getCurrentPlayerTime(player);
        CompletableFuture<Long> second = tracker.getCurrentPlayerTime(player);

        assertThat(first).isCompletedExceptionally();
        assertThat(second).isCompletedExceptionally().isNotSameAs(first);
    }

    @Test
    void immediatelyRejectedNameLookupCompletesAndDoesNotPoisonSingleFlightCache() {
        UserDataService service = UserDataService.local(temporary.resolve("names"), "test_");
        service.close();
        PlayerTimeTracker tracker = new PlayerTimeTracker(service);

        CompletableFuture<Long> first = tracker.getPlayerTimeByName("Steve");
        CompletableFuture<Long> second = tracker.getPlayerTimeByName("STEVE");

        assertThat(first).isCompletedExceptionally();
        assertThat(second).isCompletedExceptionally().isNotSameAs(first);
    }
}
