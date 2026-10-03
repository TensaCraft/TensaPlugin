package ua.co.tensa.modules.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.session.SessionResumeEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.co.tensa.modules.runtime.ModuleScheduler;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class JdaDiscordGatewayLifecycleTest {
    @TempDir
    Path tempDir;

    @Test
    void anInFlightActivationCannotOverwriteClosedGatewayState() throws Exception {
        DiscordSettings settings = DiscordTestSettings.create(tempDir);
        CountDownLatch lookupStarted = new CountDownLatch(1);
        CountDownLatch releaseLookup = new CountDownLatch(1);
        AtomicBoolean jdaClosed = new AtomicBoolean();
        JDA jda = (JDA) Proxy.newProxyInstance(JDA.class.getClassLoader(), new Class<?>[]{JDA.class},
                (ignored, method, args) -> switch (method.getName()) {
                    case "getGuildById" -> {
                        lookupStarted.countDown();
                        if (!releaseLookup.await(3, TimeUnit.SECONDS)) {
                            throw new AssertionError("Guild lookup was not released");
                        }
                        yield null;
                    }
                    case "getTextChannelById", "removeEventListener" -> null;
                    case "getResponseTotal" -> 0L;
                    case "shutdown", "shutdownNow" -> { jdaClosed.set(true); yield null; }
                    case "awaitShutdown" -> true;
                    default -> throw new AssertionError("Unexpected JDA operation " + method.getName());
                });
        try (ModuleScheduler scheduler = new ModuleScheduler("gateway-lifecycle-test",
                new ModuleScheduler.Defaults(4, 1, 4, Duration.ofSeconds(2), 1,
                        Duration.ofMillis(1), Duration.ofMillis(10), 0))) {
            JdaDiscordGateway gateway = new JdaDiscordGateway(settings, scheduler);
            var connection = JdaDiscordGateway.class.getDeclaredField("jda");
            connection.setAccessible(true);
            connection.set(gateway, jda);
            var accepting = JdaDiscordGateway.class.getDeclaredField("acceptingEvents");
            accepting.setAccessible(true);
            ((AtomicBoolean) accepting.get(gateway)).set(true);
            CompletableFuture<Void> activation = new CompletableFuture<>();
            CompletableFuture<Void> shutdown = new CompletableFuture<>();
            Thread activationThread = worker(() -> gateway.onSessionResume(new SessionResumeEvent(jda)), activation);
            Thread shutdownThread = worker(() -> gateway.close(Duration.ofMillis(100)), shutdown);
            try {
                activationThread.start();
                assertThat(lookupStarted.await(2, TimeUnit.SECONDS)).isTrue();
                shutdownThread.start();
                long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
                while (!shutdown.isDone() && shutdownThread.getState() != Thread.State.BLOCKED
                        && System.nanoTime() < deadline) {
                    Thread.sleep(1);
                }
                assertThat(shutdown.isDone() || shutdownThread.getState() == Thread.State.BLOCKED).isTrue();
                releaseLookup.countDown();
                activation.get(2, TimeUnit.SECONDS);
                shutdown.get(2, TimeUnit.SECONDS);

                assertThat(jdaClosed).isTrue();
                assertThat(gateway.runtimeState()).isEqualTo("closed");
                assertThat(gateway.slashState()).isEqualTo("closed");
                assertThat(gateway.isReady()).isFalse();
            } finally {
                releaseLookup.countDown();
                activationThread.join(3_000);
                gateway.close(Duration.ofMillis(100));
                shutdownThread.join(3_000);
            }
        }
    }

    private static Thread worker(Runnable work, CompletableFuture<Void> completion) {
        return Thread.ofPlatform().daemon(true).unstarted(() -> {
            try {
                work.run();
                completion.complete(null);
            } catch (Throwable error) {
                completion.completeExceptionally(error);
            }
        });
    }
}
