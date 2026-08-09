package ua.co.tensa.modules.discord;

import ua.co.tensa.Message;
import ua.co.tensa.modules.chat.ProxyChatMessage;
import ua.co.tensa.modules.chat.ProxyChatRelay;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import ua.co.tensa.modules.runtime.ModuleScheduler;

final class DiscordRuntime implements AutoCloseable {
    private final DiscordSettings settings;
    private final DiscordGateway gateway;
    private final DiscordLinkService links;
    private final DiscordDelivery delivery;
    private final InboundChatPublisher inboundChatPublisher;
    private final DiscordServerPolicy serverPolicy;
    private final DiscordEventRateLimiter eventRateLimiter;
    private final CommunicationsMetrics metrics;
    private final PostLinkEffects postLinkEffects;
    private final DiscordWebhookProvisioner webhookProvisioner;
    private final ModuleScheduler scheduler;
    private final BoundedWorkerQueue<DiscordInboundMessage> inboundQueue;
    private final BoundedWorkerQueue<DiscordOutboundMessage> outboundQueue;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong lastBackpressureWarning = new AtomicLong();
    private final LinkedHashMap<String, Long> guardFeedback = new LinkedHashMap<>();

    DiscordRuntime(
            DiscordSettings settings,
            DiscordGateway gateway,
            DiscordLinkService links,
            DiscordDelivery delivery
    ) {
        this(settings, gateway, links, delivery, (source, author, content) -> { }, new CommunicationsMetrics(), null, null, null);
    }

    DiscordRuntime(
            DiscordSettings settings,
            DiscordGateway gateway,
            DiscordLinkService links,
            DiscordDelivery delivery,
            InboundChatPublisher inboundChatPublisher
    ) {
        this(settings, gateway, links, delivery, inboundChatPublisher, new CommunicationsMetrics(), null, null, null);
    }

    DiscordRuntime(
            DiscordSettings settings,
            DiscordGateway gateway,
            DiscordLinkService links,
            DiscordDelivery delivery,
            InboundChatPublisher inboundChatPublisher,
            CommunicationsMetrics metrics,
            PostLinkEffects postLinkEffects
    ) {
        this(settings, gateway, links, delivery, inboundChatPublisher, metrics, postLinkEffects, null, null);
    }

    DiscordRuntime(
            DiscordSettings settings,
            DiscordGateway gateway,
            DiscordLinkService links,
            DiscordDelivery delivery,
            InboundChatPublisher inboundChatPublisher,
            CommunicationsMetrics metrics,
            PostLinkEffects postLinkEffects,
            DiscordWebhookProvisioner webhookProvisioner,
            ModuleScheduler scheduler
    ) {
        this.settings = settings;
        this.gateway = gateway;
        this.links = links;
        this.delivery = delivery;
        this.inboundChatPublisher = inboundChatPublisher;
        this.metrics = java.util.Objects.requireNonNull(metrics, "metrics");
        this.postLinkEffects = postLinkEffects;
        this.webhookProvisioner = webhookProvisioner;
        this.scheduler = scheduler;
        this.serverPolicy = new DiscordServerPolicy(settings);
        this.eventRateLimiter = new DiscordEventRateLimiter(settings.eventRatePerMinute());
        this.inboundQueue = new BoundedWorkerQueue<>(
                "tensa-discord-inbound",
                settings.queueCapacity(),
                this::deliverToMinecraft,
                (ignored, error) -> {
                    metrics.deliveryFailed(error);
                    warnRateLimited("Discord to Minecraft relay failed");
                }
        );
        this.outboundQueue = new BoundedWorkerQueue<>(
                "tensa-discord-outbound",
                settings.queueCapacity(),
                this::deliverToDiscord,
                (ignored, error) -> {
                    metrics.deliveryFailed(error);
                    warnRateLimited("Minecraft to Discord relay failed after bounded retries");
                }
        );
    }

    void start() {
        if (closed.get()) {
            throw new IllegalStateException("Discord runtime is closed");
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        inboundQueue.start();
        outboundQueue.start();
        try {
            gateway.start(this::acceptDiscordMessage, this::completeLinkFromDiscord, () -> {
                links.reconcileRoles();
                if (webhookProvisioner != null) {
                    webhookProvisioner.refreshAll();
                }
            });
        } catch (RuntimeException e) {
            close();
            throw new IllegalStateException("Discord gateway could not be started");
        }
    }

    ProxyChatRelay.Result relayMinecraftChat(ProxyChatMessage message) {
        if (!running.get()
                || !settings.minecraftToDiscord()
                || message == null
                || message.origin() != ProxyChatMessage.Origin.MINECRAFT
                || message.playerUuid() == null) {
            return ProxyChatRelay.Result.DISABLED;
        }
        if (settings.relayGuard().protects(
                DiscordRelayGuard.Direction.MINECRAFT_TO_DISCORD,
                message.channel()
        ) && !links.isPlayerLinked(message.playerUuid())) {
            metrics.blocked();
            return ProxyChatRelay.Result.LINK_REQUIRED;
        }
        String player = DiscordSanitizer.forDiscord(message.playerName(), 80);
        String content = DiscordSanitizer.forDiscord(message.message(), settings.maxDiscordMessageLength());
        if (content.isBlank()) {
            return ProxyChatRelay.Result.DISABLED;
        }
        String webhookContent = DiscordSanitizer.truncate(
                DiscordMessages.render(settings.minecraftToDiscordFormat(), java.util.Map.of(
                        "player", player,
                        "server", DiscordSanitizer.forDiscord(message.server(), 80),
                        "message", content
                )),
                settings.maxDiscordMessageLength()
        );
        String botContent = DiscordSanitizer.truncate(player + ": " + webhookContent, settings.maxDiscordMessageLength());
        String avatar = settings.avatarUrlTemplate()
                .replace("{uuid}", message.playerUuid().toString().replace("-", ""))
                .replace("{player}", URLEncoder.encode(message.playerName(), StandardCharsets.UTF_8));
        boolean accepted = outboundQueue.offer(DiscordOutboundMessage.chat(
                webhookContent,
                botContent,
                DiscordSanitizer.webhookUsername(message.playerName()),
                avatar
        ));
        if (!accepted) {
            metrics.dropped();
            warnRateLimited("Discord outbound relay queue is full; dropping a new message");
        }
        return accepted ? ProxyChatRelay.Result.ACCEPTED : ProxyChatRelay.Result.BUSY;
    }

    void announceJoin(String playerName, String serverName) {
        announce(settings.joinEmbed(), java.util.Map.of(
                "player", eventValue(playerName, 80),
                "server", serverValue(serverName)
        ));
    }

    void announceQuit(String playerName, String serverName) {
        announce(settings.quitEmbed(), java.util.Map.of(
                "player", eventValue(playerName, 80),
                "server", serverValue(serverName)
        ));
    }

    void announceServerSwitch(String playerName, String fromServer, String toServer) {
        announce(settings.serverSwitchEmbed(), java.util.Map.of(
                "player", eventValue(playerName, 80),
                "from", serverValue(fromServer),
                "to", serverValue(toServer)
        ));
    }

    void announceBackendUnavailable(String serverName) {
        announce(settings.backendUnavailableEmbed(), java.util.Map.of("server", serverValue(serverName)));
    }

    void announceBackendRecovered(String serverName) {
        announce(settings.backendRecoveredEmbed(), java.util.Map.of("server", serverValue(serverName)));
    }

    void announceAdvancement(
            String playerName,
            String serverName,
            String advancement,
            String description
    ) {
        if (settings.advancementMessages()) {
            announce(settings.advancementEmbed(), java.util.Map.of(
                    "player", eventValue(playerName, 80),
                    "server", serverValue(serverName),
                    "advancement", eventValue(advancement, 200),
                    "description", eventValue(description, 500)
            ));
        }
    }

    DiscordServerPolicy serverPolicy() {
        return serverPolicy;
    }

    DiscordLinkService.IssuedCode issueLinkCode(UUID playerUuid, String playerName) {
        return links.issue(playerUuid, playerName, Instant.now());
    }

    CompletableFuture<Optional<LinkedAccount>> linkStatus(UUID playerUuid) {
        return links.status(playerUuid);
    }

    CompletableFuture<DiscordLinkService.Result> unlink(UUID playerUuid) {
        return links.unlink(playerUuid);
    }

    String linkCommandName() {
        return settings.linkCommandName();
    }

    boolean isRunning() {
        return running.get() && inboundQueue.isRunning() && outboundQueue.isRunning();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        running.set(false);
        outboundQueue.close(Duration.ofSeconds(3));
        inboundQueue.close(Duration.ofSeconds(3));
        links.close();
        if (postLinkEffects != null) {
            postLinkEffects.close();
        }
        if (webhookProvisioner != null) {
            webhookProvisioner.close();
        }
        if (scheduler != null) {
            scheduler.cancelScope("discord-announcements");
        }
        gateway.close(Duration.ofSeconds(5));
        synchronized (guardFeedback) {
            guardFeedback.clear();
        }
    }

    private void acceptDiscordMessage(DiscordInboundMessage message) {
        if (!running.get()
                || !settings.discordToMinecraft()
                || !DiscordLoopGuard.shouldRelay(
                        message,
                        settings.guildId(),
                        settings.channelId(),
                        gateway.selfUserId()
                )) {
            return;
        }
        if (settings.relayGuard().protects(
                DiscordRelayGuard.Direction.DISCORD_TO_MINECRAFT,
                "global"
        ) && !links.isDiscordLinked(message.authorId())) {
            metrics.blocked();
            replyForRequiredLink(message);
            return;
        }
        if (!inboundQueue.offer(message)) {
            metrics.dropped();
            warnRateLimited("Discord inbound relay queue is full; dropping a new message");
        }
    }

    private void deliverToMinecraft(DiscordInboundMessage message) {
        String author = DiscordSanitizer.truncate(DiscordSanitizer.normalize(message.authorName()), 80);
        String content = DiscordSanitizer.truncate(
                DiscordSanitizer.normalize(message.content()),
                settings.maxMinecraftMessageLength()
        );
        if (content.isBlank()) {
            return;
        }
        inboundChatPublisher.publish("Discord", author, content);
    }

    private void deliverToDiscord(DiscordOutboundMessage message) throws Exception {
        long startedNanos = System.nanoTime();
        Exception lastFailure = null;
        if (delivery.requiresReadyGateway(message)) {
            awaitGatewayReconnect(message.route());
        }
        for (int attempt = 1; attempt <= settings.deliveryAttempts(); attempt++) {
            try {
                delivery.send(message).get(settings.deliveryTimeout().toMillis(), TimeUnit.MILLISECONDS);
                metrics.observedLatency(startedNanos);
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            } catch (Exception e) {
                lastFailure = e;
                if (isAmbiguousDeliveryFailure(e) || !DiscordDelivery.retryable(e)) {
                    throw e;
                }
                if (attempt < settings.deliveryAttempts()) {
                    metrics.retried();
                    if (!gateway.isReady(message.route())) {
                        awaitGatewayReconnect(message.route());
                    }
                    Thread.sleep(DiscordRetryPolicy.delayMillis(
                            attempt,
                            bound -> java.util.concurrent.ThreadLocalRandom.current().nextLong(bound)
                    ));
                }
            }
        }
        throw lastFailure == null ? new IllegalStateException("Discord delivery failed") : lastFailure;
    }

    private static boolean isAmbiguousDeliveryFailure(Throwable error) {
        Throwable cause = DiscordDiagnostics.unwrap(error);
        return cause instanceof java.util.concurrent.TimeoutException
                || cause instanceof java.net.http.HttpTimeoutException
                || cause instanceof java.io.IOException;
    }

    private void awaitGatewayReconnect(DiscordRoute route) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(settings.reconnectMaxDelaySeconds());
        while (running.get() && !gateway.isReady(route) && System.nanoTime() < deadline) {
            Thread.sleep(250L);
        }
        if (!gateway.isReady(route)) {
            throw new IllegalStateException("Discord gateway did not reconnect before the configured deadline");
        }
    }

    private CompletableFuture<DiscordEmbedMessage> completeLinkFromDiscord(
            String code,
            String discordUserId,
            String discordUserName
    ) {
        return links.complete(code, discordUserId, discordUserName)
                .thenApply(this::slashReply)
                .exceptionally(ignored -> renderLinkError(
                        "Сервіс прив'язки зараз перевантажений. Спробуйте пізніше."
                ));
    }

    private DiscordEmbedMessage slashReply(DiscordLinkService.Result result) {
        return switch (result.type()) {
            case LINKED -> renderLinkSuccess(result.account().playerName());
            case INVALID_CODE -> renderLinkError(
                    "Код недійсний або вже використаний. Створіть новий через /discord link у Minecraft."
            );
            case EXPIRED_CODE -> renderLinkError(
                    "Термін дії коду минув. Створіть новий через /discord link у Minecraft."
            );
            case PLAYER_ALREADY_LINKED -> renderLinkError(
                    "Цей Minecraft-акаунт уже прив'язаний."
            );
            case DISCORD_ALREADY_LINKED -> renderLinkError(
                    "Цей Discord-акаунт уже прив'язаний до іншого гравця."
            );
            case ROLE_FAILED -> renderLinkError(
                    "Не вдалося видати роль. Прив'язку не збережено; зверніться до адміністратора."
            );
            case BUSY -> renderLinkError(
                    "Сервіс прив'язки зараз перевантажений. Спробуйте пізніше."
            );
            default -> renderLinkError(
                    "Не вдалося зберегти прив'язку. Спробуйте ще раз пізніше."
            );
        };
    }

    private void announce(DiscordEmbedTemplate template, java.util.Map<String, String> values) {
        if (scheduler == null) {
            enqueueAnnouncement(template, values);
            return;
        }
        long sequence = announcementSequence.incrementAndGet();
        try {
            scheduler.schedule(ModuleScheduler.job("discord-announcement-" + sequence,
                            () -> enqueueAnnouncement(template, values))
                    .scope("discord-announcements")
                    .dedupe("discord-announcement-" + sequence)
                    .timeout(Duration.ofSeconds(5))
                    .onDeadLetter(metrics::recordFailure)
                    .build());
        } catch (RuntimeException full) {
            metrics.dropped();
            metrics.recordFailure(full);
        }
    }

    private final AtomicLong announcementSequence = new AtomicLong();

    private void enqueueAnnouncement(DiscordEmbedTemplate template, java.util.Map<String, String> values) {
        if (!template.enabled() || !running.get() || !eventRateLimiter.tryAcquire(Instant.now())) {
            return;
        }
        DiscordOutboundMessage outbound;
        try {
            outbound = DiscordOutboundMessage.announcement(template.render(values));
        } catch (IllegalArgumentException localValidationFailure) {
            metrics.recordFailure(localValidationFailure);
            String plain = DiscordSanitizer.truncate(
                    DiscordMessages.render(template.description(), values),
                    settings.maxDiscordMessageLength()
            );
            outbound = DiscordOutboundMessage.announcementPlain(plain);
        }
        if (!outboundQueue.offer(outbound)) {
            metrics.dropped();
            warnRateLimited("Discord outbound relay queue is full; dropping an announcement");
        }
    }

    private DiscordEmbedMessage renderLinkSuccess(String playerName) {
        try {
            return settings.linkSuccessEmbed().render(java.util.Map.of("player", eventValue(playerName, 80)));
        } catch (IllegalArgumentException invalid) {
            metrics.recordFailure(invalid);
            return DiscordEmbedMessage.success("Акаунт успішно прив'язано.");
        }
    }

    private DiscordEmbedMessage renderLinkError(String message) {
        try {
            return settings.linkErrorEmbed().render(java.util.Map.of("message", eventValue(message, 4_000)));
        } catch (IllegalArgumentException invalid) {
            metrics.recordFailure(invalid);
            return DiscordEmbedMessage.linkError(message);
        }
    }

    private String serverValue(String serverName) {
        return eventValue(serverPolicy.label(serverName), 80);
    }

    private static String eventValue(String value, int maximum) {
        return DiscordSanitizer.forDiscord(value, maximum);
    }

    CommunicationsMetrics.Snapshot diagnostics(String storageBackend) {
        return metrics.snapshot(
                isRunning() ? "running" : closed.get() ? "closed" : "degraded",
                gateway.runtimeState(),
                gateway.slashState(),
                storageBackend,
                inboundQueue.size(),
                outboundQueue.size(),
                postLinkEffects == null ? 0 : postLinkEffects.queueDepth(),
                gateway.reconnectCount()
        );
    }

    private void replyForRequiredLink(DiscordInboundMessage message) {
        if (!allowGuardFeedback(message.authorId())) {
            return;
        }
        gateway.sendTemporaryReply(
                message.channelId(),
                message.messageId(),
                settings.relayGuard().discordReply(),
                settings.relayGuard().discordReplyDeleteAfter()
        ).exceptionally(error -> {
            metrics.deliveryFailed(error);
            return null;
        });
    }

    private boolean allowGuardFeedback(String authorId) {
        long now = System.nanoTime();
        long cooldown = settings.relayGuard().feedbackCooldown().toNanos();
        synchronized (guardFeedback) {
            Long previous = guardFeedback.get(authorId);
            if (previous != null && now - previous < cooldown) {
                return false;
            }
            guardFeedback.remove(authorId);
            guardFeedback.put(authorId, now);
            while (guardFeedback.size() > settings.eventStateCapacity()) {
                String eldest = guardFeedback.keySet().iterator().next();
                guardFeedback.remove(eldest);
            }
            return true;
        }
    }

    private void warnRateLimited(String warning) {
        long now = System.nanoTime();
        long previous = lastBackpressureWarning.get();
        if (previous == 0L || now - previous >= TimeUnit.SECONDS.toNanos(30)) {
            if (lastBackpressureWarning.compareAndSet(previous, now)) {
                Message.warn(warning);
            }
        }
    }

    void cleanupExpiredLinkCodes() {
        links.cleanupExpiredCodes(Instant.now());
    }

    ResourceState resourceState() {
        int feedback;
        synchronized (guardFeedback) {
            feedback = guardFeedback.size();
        }
        return new ResourceState(links.codeCount(), links.linkCount(), feedback);
    }

    record ResourceState(int linkCodes, int linkIndexEntries, int guardFeedbackEntries) {
        static ResourceState empty() {
            return new ResourceState(0, 0, 0);
        }
    }

    @FunctionalInterface
    interface InboundChatPublisher {
        void publish(String source, String author, String content);
    }
}
