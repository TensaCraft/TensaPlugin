package ua.co.tensa.modules.rcon.manager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RconManagerLifecycleTest {
    @AfterEach
    void stopRuntime() throws Exception {
        shutdown();
    }

    @Test
    void inactiveModuleCannotRecreateExecutorThroughStaleCommand() throws Exception {
        shutdown();
        CompletableFuture<String> result = RconManagerModule.supplyAsync(() -> "unexpected execution");
        result.handle((value, failure) -> null).get(2, TimeUnit.SECONDS);

        assertThat(result).isCompletedExceptionally();
    }

    @Test
    void forcedShutdownCompletesQueuedCommandFutureWithoutExecutingIt() throws Exception {
        ExecutorService blocked = Executors.newFixedThreadPool(2);
        var field = RconManagerModule.class.getDeclaredField("commandExecutor");
        field.setAccessible(true);
        field.set(null, blocked);
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean executed = new java.util.concurrent.atomic.AtomicBoolean();
        try {
            for (int index = 0; index < 2; index++) {
                RconManagerModule.supplyAsync(() -> {
                    started.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                });
            }
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<Boolean> queued = RconManagerModule.supplyAsync(() -> {
                executed.set(true);
                return true;
            });

            shutdown();

            assertThat(queued).isDone();
            assertThat(executed).isFalse();
        } finally {
            release.countDown();
            blocked.shutdownNow();
        }
    }

    private static void shutdown() throws Exception {
        var method = RconManagerModule.class.getDeclaredMethod("shutdownExecutor");
        method.setAccessible(true);
        method.invoke(null);
    }
}
