package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AtomicLinkStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void persistsReloadsAndUnlinksWithoutLeavingTemporaryFiles() throws Exception {
        Path relative = Path.of("discord", "links.json");
        AtomicLinkStore store = new AtomicLinkStore(tempDir, relative);
        store.load();
        LinkedAccount account = new LinkedAccount(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "Pilot",
                "12345678901234567",
                "Discord Pilot",
                Instant.parse("2026-08-02T09:00:00Z")
        );

        assertThat(store.link(account)).isEqualTo(AtomicLinkStore.LinkOutcome.LINKED);

        AtomicLinkStore reloaded = new AtomicLinkStore(tempDir, relative);
        reloaded.load();
        assertThat(reloaded.findByPlayer(account.playerUuid())).contains(account);
        assertThat(reloaded.findByDiscord(account.discordUserId())).contains(account);
        assertThat(reloaded.unlink(account.playerUuid())).contains(account);
        assertThat(reloaded.findByPlayer(account.playerUuid())).isEmpty();
        assertThat(Files.list(tempDir.resolve("discord"))
                .filter(path -> path.getFileName().toString().endsWith(".tmp")))
                .isEmpty();
    }

    @Test
    void rejectsConflictingBindings() throws Exception {
        AtomicLinkStore store = new AtomicLinkStore(tempDir, Path.of("links.json"));
        store.load();
        LinkedAccount first = account("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "11111111111111111");
        LinkedAccount samePlayer = account("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "22222222222222222");
        LinkedAccount sameDiscord = account("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", "11111111111111111");

        assertThat(store.link(first)).isEqualTo(AtomicLinkStore.LinkOutcome.LINKED);
        assertThat(store.link(samePlayer)).isEqualTo(AtomicLinkStore.LinkOutcome.PLAYER_LINKED_ELSEWHERE);
        assertThat(store.link(sameDiscord)).isEqualTo(AtomicLinkStore.LinkOutcome.DISCORD_LINKED_ELSEWHERE);
    }

    private static LinkedAccount account(String playerUuid, String discordId) {
        return new LinkedAccount(
                UUID.fromString(playerUuid),
                "Pilot",
                discordId,
                "Discord Pilot",
                Instant.parse("2026-08-02T09:00:00Z")
        );
    }
}
