package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.co.tensa.core.storage.CoreStorageService;

import java.nio.file.Path;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcDiscordLinkRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void persistsLinksThroughTheCoreStorageBackend() throws Exception {
        Path database = tempDir.resolve("storage").resolve("core");
        LinkedAccount account = account("00000000-0000-0000-0000-000000000001", "10000000000000001");

        try (CoreStorageService storage = CoreStorageService.local(database, "tpl_")) {
            DiscordLinkRepository links = new JdbcDiscordLinkRepository(storage, 100);
            links.initialize();
            assertThat(links.link(account)).isEqualTo(DiscordLinkRepository.LinkOutcome.LINKED);
            assertThat(links.findByPlayer(account.playerUuid())).contains(account);
            assertThat(links.findByDiscord(account.discordUserId())).contains(account);
        }

        try (CoreStorageService storage = CoreStorageService.local(database, "tpl_")) {
            DiscordLinkRepository links = new JdbcDiscordLinkRepository(storage, 100);
            links.initialize();
            assertThat(links.findByPlayer(account.playerUuid())).contains(account);
            assertThat(links.all()).containsExactly(account);
            assertThat(links.unlink(account.playerUuid())).contains(account);
            assertThat(links.findByDiscord(account.discordUserId())).isEmpty();
        }
    }

    @Test
    void reportsPlayerAndDiscordConflictsWithoutOverwritingTheStoredLink() throws Exception {
        try (CoreStorageService storage = CoreStorageService.local(tempDir.resolve("conflicts"), "tpl_")) {
            DiscordLinkRepository links = new JdbcDiscordLinkRepository(storage, 100);
            links.initialize();
            LinkedAccount first = account("00000000-0000-0000-0000-000000000001", "10000000000000001");
            LinkedAccount same = account("00000000-0000-0000-0000-000000000001", "10000000000000001");
            LinkedAccount playerConflict = account("00000000-0000-0000-0000-000000000001", "10000000000000002");
            LinkedAccount discordConflict = account("00000000-0000-0000-0000-000000000002", "10000000000000001");

            assertThat(links.link(first)).isEqualTo(DiscordLinkRepository.LinkOutcome.LINKED);
            assertThat(links.link(same)).isEqualTo(DiscordLinkRepository.LinkOutcome.ALREADY_LINKED);
            assertThat(links.link(playerConflict)).isEqualTo(DiscordLinkRepository.LinkOutcome.PLAYER_LINKED_ELSEWHERE);
            assertThat(links.link(discordConflict)).isEqualTo(DiscordLinkRepository.LinkOutcome.DISCORD_LINKED_ELSEWHERE);
            assertThat(links.all()).containsExactly(first);
        }
    }

    @Test
    void databaseUniquenessAlsoProtectsConcurrentRepositoryInstances() throws Exception {
        try (CoreStorageService storage = CoreStorageService.local(tempDir.resolve("concurrent"), "tpl_")) {
            DiscordLinkRepository first = new JdbcDiscordLinkRepository(storage, 100);
            DiscordLinkRepository second = new JdbcDiscordLinkRepository(storage, 100);
            first.initialize();
            second.initialize();
            CountDownLatch start = new CountDownLatch(1);
            LinkedAccount left = account("00000000-0000-0000-0000-000000000001", "10000000000000001");
            LinkedAccount right = account("00000000-0000-0000-0000-000000000002", "10000000000000001");

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var leftResult = executor.submit(() -> { start.await(); return first.link(left); });
                var rightResult = executor.submit(() -> { start.await(); return second.link(right); });
                start.countDown();
                assertThat(java.util.List.of(leftResult.get(), rightResult.get()))
                        .containsExactlyInAnyOrder(
                                DiscordLinkRepository.LinkOutcome.LINKED,
                                DiscordLinkRepository.LinkOutcome.DISCORD_LINKED_ELSEWHERE
                        );
            }
        }
    }

    @Test
    void rejectsAStoredDirectoryThatExceedsTheConfiguredBound() throws Exception {
        Path database = tempDir.resolve("bounded");
        try (CoreStorageService storage = CoreStorageService.local(database, "tpl_")) {
            DiscordLinkRepository writer = new JdbcDiscordLinkRepository(storage, 10);
            writer.initialize();
            writer.link(account("00000000-0000-0000-0000-000000000001", "10000000000000001"));
            writer.link(account("00000000-0000-0000-0000-000000000002", "10000000000000002"));

            DiscordLinkRepository bounded = new JdbcDiscordLinkRepository(storage, 1);
            assertThatThrownBy(bounded::initialize)
                    .isInstanceOf(java.io.IOException.class)
                    .hasMessageContaining("too many account bindings");
        }
    }

    @Test
    void committedLinkRemainsSuccessfulWhenConnectionCleanupFails() throws Exception {
        for (String cleanup : java.util.List.of("setAutoCommit", "close")) {
            try (CoreStorageService database = CoreStorageService.local(tempDir.resolve("link-" + cleanup), "tpl_");
                 CoreStorageService faulty = CoreStorageService.external(failCleanupAfterCommit(database.dataSource(), cleanup), "tpl_")) {
                DiscordLinkRepository links = new JdbcDiscordLinkRepository(faulty, 100);
                links.initialize();
                LinkedAccount account = account("00000000-0000-0000-0000-000000000001", "10000000000000001");

                assertThatCode(() -> assertThat(links.link(account)).isEqualTo(DiscordLinkRepository.LinkOutcome.LINKED))
                        .doesNotThrowAnyException();

                assertThat(links.findByPlayer(account.playerUuid())).contains(account);
                DiscordLinkRepository persisted = new JdbcDiscordLinkRepository(database, 100);
                persisted.initialize();
                assertThat(persisted.findByPlayer(account.playerUuid())).contains(account);
            }
        }
    }

    @Test
    void committedUnlinkRemainsSuccessfulWhenConnectionCleanupFails() throws Exception {
        for (String cleanup : java.util.List.of("setAutoCommit", "close")) {
            try (CoreStorageService database = CoreStorageService.local(tempDir.resolve("unlink-" + cleanup), "tpl_");
                 CoreStorageService faulty = CoreStorageService.external(failCleanupAfterCommit(database.dataSource(), cleanup), "tpl_")) {
                DiscordLinkRepository persisted = new JdbcDiscordLinkRepository(database, 100);
                persisted.initialize();
                LinkedAccount account = account("00000000-0000-0000-0000-000000000001", "10000000000000001");
                persisted.link(account);
                DiscordLinkRepository links = new JdbcDiscordLinkRepository(faulty, 100);
                links.initialize();

                assertThatCode(() -> assertThat(links.unlink(account.playerUuid())).contains(account))
                        .doesNotThrowAnyException();

                assertThat(links.findByPlayer(account.playerUuid())).isEmpty();
                persisted.initialize();
                assertThat(persisted.findByPlayer(account.playerUuid())).isEmpty();
            }
        }
    }

    private static DataSource failCleanupAfterCommit(DataSource delegate, String cleanupMethod) {
        return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[]{DataSource.class},
                (ignored, method, args) -> {
                    Object result;
                    try {
                        result = method.invoke(delegate, args);
                    } catch (InvocationTargetException error) {
                        throw error.getCause();
                    }
                    if (!(result instanceof Connection connection)) {
                        return result;
                    }
                    AtomicBoolean committed = new AtomicBoolean();
                    return Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                            (proxy, operation, arguments) -> {
                                Object value;
                                try {
                                    value = operation.invoke(connection, arguments);
                                } catch (InvocationTargetException error) {
                                    throw error.getCause();
                                }
                                if (operation.getName().equals("commit")) {
                                    committed.set(true);
                                } else if (committed.get() && operation.getName().equals(cleanupMethod)) {
                                    throw new SQLException("simulated post-commit cleanup failure");
                                }
                                return value;
                            });
                });
    }

    private static LinkedAccount account(String playerUuid, String discordId) {
        return new LinkedAccount(
                UUID.fromString(playerUuid),
                "Player" + playerUuid.charAt(playerUuid.length() - 1),
                discordId,
                "DiscordUser",
                Instant.parse("2026-08-04T12:00:00Z")
        );
    }
}
