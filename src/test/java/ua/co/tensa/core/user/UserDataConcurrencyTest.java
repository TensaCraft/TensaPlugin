package ua.co.tensa.core.user;

import org.junit.jupiter.api.Test;
import ua.co.tensa.core.meta.UserMetaService;
import ua.co.tensa.core.storage.CoreStorageService;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Optional;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class UserDataConcurrencyTest {
    private final UUID uuid = UUID.randomUUID();

    @Test
    void synchronousMetadataReadInsideDataCallbackDoesNotWaitOnItsOwnWorker() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        try (UserDataService data = service((method, args) -> {
            if (method.equals("getMeta")) {
                firstStarted.countDown();
                releaseFirst.await(2, TimeUnit.SECONDS);
                return Optional.empty();
            }
            return method.equals("getAllMeta") ? Map.of("rank", "stored") : null;
        }); UserMetaService meta = new UserMetaService(data, true)) {
            try {
                CompletableFuture<Optional<String>> first = data.getMetaAsync(uuid, "start");
                assertThat(firstStarted.await(2, TimeUnit.SECONDS)).isTrue();
                CompletableFuture<Map<String, String>> result = first.thenApply(ignored -> meta.getAll(uuid));
                releaseFirst.countDown();

                assertThat(result).succeedsWithin(Duration.ofSeconds(2))
                        .satisfies(values -> assertThat(values).containsEntry("rank", "stored"));
            } finally {
                releaseFirst.countDown();
            }
        }
    }

    @Test
    void forcedShutdownCompletesQueuedFuturesInsteadOfLeavingThemPending() throws Exception {
        CountDownLatch readStarted = new CountDownLatch(1);
        CountDownLatch releaseRead = new CountDownLatch(1);
        try (UserDataService data = service((method, args) -> {
            if (method.equals("getAllMeta")) {
                readStarted.countDown();
                boolean released = false;
                while (!released) {
                    try {
                        released = releaseRead.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException ignored) {
                        // A JDBC driver may not abort an in-flight operation on interruption.
                    }
                }
                return Map.of();
            }
            return null;
        })) {
            try {
                CompletableFuture<Map<String, String>> running = data.getAllMetaAsync(uuid);
                assertThat(readStarted.await(2, TimeUnit.SECONDS)).isTrue();
                CompletableFuture<Void> queued = data.setMetaAsync(uuid, "rank", "new");
                data.close();
                assertThat(queued).isCompletedExceptionally();
                assertThat(running).isCompletedExceptionally();
            } finally {
                releaseRead.countDown();
            }
        }
    }

    @Test
    void asyncWritesPersistInSubmissionOrder() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        Map<String, String> database = new ConcurrentHashMap<>();
        try (UserDataService data = service((method, args) -> {
            if (method.equals("setMeta")) {
                if (args[2].equals("old")) {
                    firstStarted.countDown();
                    releaseFirst.await(5, TimeUnit.SECONDS);
                } else {
                    secondStarted.countDown();
                }
                database.put((String) args[1], (String) args[2]);
            }
            return null;
        })) {
            try {
                CompletableFuture<Void> first = data.setMetaAsync(uuid, "rank", "old");
                assertThat(firstStarted.await(2, TimeUnit.SECONDS)).isTrue();
                CompletableFuture<Void> second = data.setMetaAsync(uuid, "rank", "new");
                boolean overtook = secondStarted.await(200, TimeUnit.MILLISECONDS);
                releaseFirst.countDown();
                CompletableFuture.allOf(first, second).get(2, TimeUnit.SECONDS);
                assertThat(overtook).as("later writes must not overtake a pending write").isFalse();
                assertThat(database).containsEntry("rank", "new");
            } finally {
                releaseFirst.countDown();
            }
        }
    }

    @Test
    void settingBeforeInitialLoadPreservesOtherPersistedKeys() throws Exception {
        Map<String, String> database = new ConcurrentHashMap<>(Map.of("existing", "kept"));
        try (UserDataService data = service((method, args) -> switch (method) {
            case "getAllMeta" -> Map.copyOf(database);
            case "setMeta" -> database.put((String) args[1], (String) args[2]);
            default -> null;
        }); UserMetaService meta = new UserMetaService(data, true)) {
            meta.set(uuid, "rank", "new", false);
            assertThat(meta.getAllAsync(uuid).get(2, TimeUnit.SECONDS))
                    .containsEntry("existing", "kept")
                    .containsEntry("rank", "new");
        }
    }

    @Test
    void inFlightLoadCannotOverwriteNewerSetOrResurrectDeletedKey() throws Exception {
        CountDownLatch readStarted = new CountDownLatch(1);
        CountDownLatch releaseRead = new CountDownLatch(1);
        try (UserDataService data = service((method, args) -> {
            if (method.equals("getAllMeta")) {
                readStarted.countDown();
                releaseRead.await(5, TimeUnit.SECONDS);
                return Map.of("rank", "old", "deleted", "old", "existing", "kept");
            }
            return null;
        }); UserMetaService meta = new UserMetaService(data, true)) {
            try {
                CompletableFuture<Map<String, String>> read = meta.getAllAsync(uuid);
                assertThat(readStarted.await(2, TimeUnit.SECONDS)).isTrue();
                meta.set(uuid, "rank", "new", false);
                meta.delete(uuid, "deleted", false);
                releaseRead.countDown();
                assertThat(read.get(2, TimeUnit.SECONDS))
                        .containsEntry("rank", "new")
                        .containsEntry("existing", "kept")
                        .doesNotContainKey("deleted");
            } finally {
                releaseRead.countDown();
            }
        }
    }

    @Test
    void forgettingDuringLoadDoesNotRestoreDisconnectedPlayersCache() throws Exception {
        CountDownLatch readStarted = new CountDownLatch(1);
        CountDownLatch releaseRead = new CountDownLatch(1);
        try (UserDataService data = service((method, args) -> {
            if (method.equals("getAllMeta")) {
                readStarted.countDown();
                releaseRead.await(5, TimeUnit.SECONDS);
                return Map.of("rank", "old");
            }
            return null;
        }); UserMetaService meta = new UserMetaService(data, true)) {
            try {
                meta.preloadAsync(uuid);
                assertThat(readStarted.await(2, TimeUnit.SECONDS)).isTrue();
                meta.forget(uuid);
                releaseRead.countDown();
                data.close();
                assertThat(meta.getCached(uuid)).isEmpty();
            } finally {
                releaseRead.countDown();
            }
        }
    }

    private UserDataService service(StoreOperation operation) throws Exception {
        UserDataStore store = (UserDataStore) Proxy.newProxyInstance(
                UserDataStore.class.getClassLoader(), new Class<?>[]{UserDataStore.class},
                (proxy, method, args) -> operation.invoke(method.getName(), args));
        Constructor<UserDataService> constructor = UserDataService.class
                .getDeclaredConstructor(UserDataStore.class, CoreStorageService.class);
        constructor.setAccessible(true);
        return constructor.newInstance(store, null);
    }

    @FunctionalInterface
    private interface StoreOperation {
        Object invoke(String method, Object[] args) throws Exception;
    }
}
