package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordLinkServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void durableLinkAssignsRoleOnceAndCanReconcileItOnPlayerEvents() throws Exception {
        DiscordSettings settings = DiscordTestSettings.create(tempDir);
        InMemoryDiscordLinkRepository repository = new InMemoryDiscordLinkRepository();
        repository.initialize();
        LinkGateway gateway = new LinkGateway();
        UUID playerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        try (DiscordLinkService service = new DiscordLinkService(
                repository,
                new LinkCodeRegistry(settings.linkCodeTtl(), settings.linkCodeLength()),
                gateway,
                settings
        )) {
            DiscordLinkService.IssuedCode issued = service.issue(playerId, "Pilot", Instant.now());
            DiscordLinkService.Result result = service.complete(
                    issued.code(), "12345678901234567", "PilotDiscord").get();

            assertThat(result.type()).isEqualTo(DiscordLinkService.ResultType.LINKED);
            assertThat(repository.findByPlayer(playerId)).isPresent();
            assertThat(gateway.assignments).hasValue(1);
            awaitValue(gateway.nicknameSyncs, 1);
            assertThat(gateway.lastNickname).isEqualTo("Pilot");
            service.reconcileRole(playerId);
            awaitValue(gateway.assignments, 2);
            awaitValue(gateway.nicknameSyncs, 2);
            assertThat(service.complete(issued.code(), "12345678901234567", "PilotDiscord").get().type())
                    .isEqualTo(DiscordLinkService.ResultType.INVALID_CODE);
            assertThat(gateway.assignments).hasValue(2);
        }
    }

    @Test
    void permanentNicknameFailureDoesNotRollBackDurableLink() throws Exception {
        DiscordSettings settings = DiscordTestSettings.create(tempDir);
        InMemoryDiscordLinkRepository repository = new InMemoryDiscordLinkRepository();
        repository.initialize();
        LinkGateway gateway = new LinkGateway();
        gateway.nicknameResult = DiscordGateway.NicknameSyncResult.MISSING_PERMISSION;
        UUID playerId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        try (DiscordLinkService service = new DiscordLinkService(
                repository,
                new LinkCodeRegistry(settings.linkCodeTtl(), settings.linkCodeLength()),
                gateway,
                settings
        )) {
            DiscordLinkService.IssuedCode issued = service.issue(playerId, "PersistentPilot", Instant.now());
            DiscordLinkService.Result result = service.complete(
                    issued.code(), "32345678901234567", "DiscordPilot").get();

            assertThat(result.type()).isEqualTo(DiscordLinkService.ResultType.LINKED);
            awaitValue(gateway.nicknameSyncs, 1);
            assertThat(repository.findByPlayer(playerId)).isPresent();
        }
    }

    @Test
    void oneNicknameExceptionDoesNotStopStartupReconciliationForLaterAccounts() throws Exception {
        DiscordSettings settings = DiscordTestSettings.create(tempDir, config -> config.linkedRoleId = "");
        InMemoryDiscordLinkRepository repository = new InMemoryDiscordLinkRepository();
        repository.initialize();
        repository.link(new LinkedAccount(
                UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"), "First", "42345678901234567", "One", Instant.now()));
        repository.link(new LinkedAccount(
                UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"), "Second", "52345678901234567", "Two", Instant.now()));
        LinkGateway gateway = new LinkGateway();
        gateway.nicknameExceptionsRemaining.set(1);

        try (DiscordLinkService service = new DiscordLinkService(
                repository,
                new LinkCodeRegistry(settings.linkCodeTtl(), settings.linkCodeLength()),
                gateway,
                settings
        )) {
            service.reconcileRoles();
            awaitValue(gateway.nicknameSyncs, 2);
        }
    }

    @Test
    void linkDoesNotPerformDiscordRoleCallsWhenNoRoleIsConfigured() throws Exception {
        DiscordSettings settings = DiscordTestSettings.create(tempDir, config -> config.linkedRoleId = "");
        InMemoryDiscordLinkRepository repository = new InMemoryDiscordLinkRepository();
        repository.initialize();
        LinkGateway gateway = new LinkGateway();
        UUID playerId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        try (DiscordLinkService service = new DiscordLinkService(
                repository,
                new LinkCodeRegistry(settings.linkCodeTtl(), settings.linkCodeLength()),
                gateway,
                settings
        )) {
            DiscordLinkService.IssuedCode issued = service.issue(playerId, "NoRole", Instant.now());
            assertThat(service.complete(issued.code(), "22345678901234567", "NoRoleDiscord").get().type())
                    .isEqualTo(DiscordLinkService.ResultType.LINKED);
            assertThat(gateway.assignments).hasValue(0);
            awaitValue(gateway.nicknameSyncs, 1);
        }
    }

    @Test
    void startupReconciliationCannotRestoreARoleAfterUnlink() throws Exception {
        DiscordSettings settings = DiscordTestSettings.create(tempDir);
        InMemoryDiscordLinkRepository repository = new InMemoryDiscordLinkRepository();
        UUID playerId = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        repository.link(new LinkedAccount(playerId, "Pilot", "62345678901234567", "Pilot", Instant.now()));
        LinkGateway gateway = new LinkGateway();
        gateway.assignmentResponse = new CompletableFuture<>();

        try (DiscordLinkService service = new DiscordLinkService(
                repository, new LinkCodeRegistry(settings.linkCodeTtl(), settings.linkCodeLength()), gateway, settings)) {
            service.reconcileRoles();
            assertThat(gateway.assignmentStarted.await(2, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<DiscordLinkService.Result> unlink = service.unlink(playerId);
            // Give an incorrectly concurrent unlink time to finish before the pending assignment.
            gateway.roleRemoved.await(200, TimeUnit.MILLISECONDS);
            gateway.assignmentResponse.complete(null);

            assertThat(unlink.get(2, TimeUnit.SECONDS).type()).isEqualTo(DiscordLinkService.ResultType.UNLINKED);
            assertThat(repository.findByPlayer(playerId)).isEmpty();
            assertThat(gateway.hasLinkedRole).isFalse();
        } finally {
            gateway.assignmentResponse.complete(null);
        }
    }

    @Test
    void shutdownCompletesRequestsDiscardedFromTheLinkingQueue() throws Exception {
        DiscordSettings settings = DiscordTestSettings.create(tempDir);
        InMemoryDiscordLinkRepository repository = new InMemoryDiscordLinkRepository();
        LinkGateway gateway = new LinkGateway();
        gateway.assignmentResponse = new CompletableFuture<>();
        UUID playerId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        DiscordLinkService service = new DiscordLinkService(
                repository, new LinkCodeRegistry(settings.linkCodeTtl(), settings.linkCodeLength()), gateway, settings);
        try {
            String code = service.issue(playerId, "Pilot", Instant.now()).code();
            CompletableFuture<DiscordLinkService.Result> link = service.complete(code, "72345678901234567", "Pilot");
            assertThat(gateway.assignmentStarted.await(2, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<?> queuedStatus = service.status(playerId);

            service.close();

            assertThat(queuedStatus).isCompletedExceptionally();
            assertThat(link).isDone();
            assertThat(service.status(playerId)).isCompletedExceptionally();
        } finally {
            gateway.assignmentResponse.complete(null);
            service.close();
        }
    }

    private static void awaitValue(AtomicInteger value, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (value.get() < expected && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(value).hasValue(expected);
    }

    private static final class LinkGateway implements DiscordGateway {
        private final AtomicInteger assignments = new AtomicInteger();
        private final AtomicInteger nicknameSyncs = new AtomicInteger();
        private final AtomicInteger nicknameExceptionsRemaining = new AtomicInteger();
        private final AtomicBoolean hasLinkedRole = new AtomicBoolean();
        private final CountDownLatch assignmentStarted = new CountDownLatch(1);
        private final CountDownLatch roleRemoved = new CountDownLatch(1);
        private CompletableFuture<Void> assignmentResponse = CompletableFuture.completedFuture(null);
        private volatile String lastNickname = "";
        private volatile DiscordGateway.NicknameSyncResult nicknameResult = DiscordGateway.NicknameSyncResult.SUCCESS;
        @Override public void start(java.util.function.Consumer<DiscordInboundMessage> inbound, SlashLinkHandler slash, Runnable ready) { }
        @Override public CompletableFuture<Void> sendBotMessage(DiscordRoute route, String content) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> sendBotEmbed(DiscordRoute route, DiscordEmbedMessage embed) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> assignLinkedRole(String id) {
            assignments.incrementAndGet();
            assignmentStarted.countDown();
            return assignmentResponse.thenRun(() -> hasLinkedRole.set(true));
        }
        @Override public CompletableFuture<Void> removeLinkedRole(String id) {
            hasLinkedRole.set(false);
            roleRemoved.countDown();
            return CompletableFuture.completedFuture(null);
        }
        @Override public CompletableFuture<NicknameSyncResult> syncNickname(String id, String nickname) {
            lastNickname = nickname;
            nicknameSyncs.incrementAndGet();
            if (nicknameExceptionsRemaining.getAndUpdate(value -> Math.max(0, value - 1)) > 0) {
                return CompletableFuture.failedFuture(new IllegalStateException("simulated Discord failure"));
            }
            return CompletableFuture.completedFuture(nicknameResult);
        }
        @Override public boolean isReady() { return true; }
        @Override public String selfUserId() { return "bot"; }
        @Override public void close(Duration timeout) { }
    }
}
