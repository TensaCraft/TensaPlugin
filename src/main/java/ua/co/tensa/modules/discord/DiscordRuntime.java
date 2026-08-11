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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import ua.co.tensa.modules.runtime.ModuleScheduler;

final class DiscordRuntime implements AutoCloseable {
    private static final int DISCORD_MESSAGE_LIMIT = 2_000;
    private final DiscordSettings settings;
    private final DiscordGateway gateway;
    private final DiscordLinkService links;
    private final DiscordDelivery delivery;
    private final InboundChatPublisher inboundChatPublisher;
    private final DiscordServerPolicy serverPolicy;
    private final DiscordEventRateLimiter eventRateLimiter;
    private final CommunicationsMetrics metrics;
    private final DiscordWebhookProvisioner webhookProvisioner;
    private final ModuleScheduler scheduler;
    private final BoundedWorkerQueue<DiscordInboundMessage> inboundQueue;
    private final BoundedWorkerQueue<DiscordOutboundMessage> outboundQueue;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong lastBackpressureWarning = new AtomicLong();

    DiscordRuntime(
            DiscordSettings settings,
            DiscordGateway gateway,
            DiscordLinkService links,
            DiscordDelivery delivery
    ) {
        this(settings, gateway, links, delivery, (source, author, content) -> { }, new CommunicationsMetrics(), null, null);
    }

    DiscordRuntime(
            DiscordSettings settings,
            DiscordGateway gateway,
            DiscordLinkService links,
            DiscordDelivery delivery,
            InboundChatPublisher inboundChatPublisher
    ) {
        this(settings, gateway, links, delivery, inboundChatPublisher, new CommunicationsMetrics(), null, null);
    }

    DiscordRuntime(
            DiscordSettings settings,
            DiscordGateway gateway,
            DiscordLinkService links,
            DiscordDelivery delivery,
            InboundChatPublisher inboundChatPublisher,
            CommunicationsMetrics metrics,
            DiscordWebhookProvisioner webhookProvisioner,
            ModuleScheduler scheduler
    ) {
        this.settings = settings;
        this.gateway = gateway;
        this.links = links;
        this.delivery = delivery;
        this.inboundChatPublisher = inboundChatPublisher;
        this.metrics = java.util.Objects.requireNonNull(metrics, "metrics");
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
        String player = DiscordSanitizer.forDiscord(message.playerName(), 80);
        String content = DiscordSanitizer.forDiscord(message.message());
        if (content.isBlank()) {
            return ProxyChatRelay.Result.DISABLED;
        }
        String webhookContent = DiscordMessages.render(
                settings.minecraftToDiscordFormat(), java.util.Map.of(
                        "player", player,
                        "server", DiscordSanitizer.forDiscord(message.server(), 80),
                        "message", content
                ));
        String avatar = ua.co.tensa.text.TextPipeline.interpolate(
                settings.avatarUrlTemplate(),
                java.util.Map.of(
                        "uuid", message.playerUuid().toString().replace("-", ""),
                        "player", URLEncoder.encode(message.playerName(), StandardCharsets.UTF_8)
                )
        );
        String webhookUsername = DiscordSanitizer.webhookUsername(message.playerName());
        java.util.List<DiscordOutboundMessage> outbound = DiscordMessageChunks
                .split(webhookContent, DISCORD_MESSAGE_LIMIT)
                .stream()
                .map(chunk -> DiscordOutboundMessage.chat(chunk, webhookUsername, avatar))
                .toList();
        boolean accepted = outboundQueue.offerAll(outbound);
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
        if (webhookProvisioner != null) {
            webhookProvisioner.close();
        }
        if (scheduler != null) {
            scheduler.cancelScope("discord-announcements");
        }
        gateway.close(Duration.ofSeconds(5));
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
        if (!inboundQueue.offer(message)) {
            metrics.dropped();
            warnRateLimited("Discord inbound relay queue is full; dropping a new message");
        }
    }

    private void deliverToMinecraft(DiscordInboundMessage message) {
        String author = DiscordSanitizer.truncate(DiscordSanitizer.normalize(message.authorName()), 80);
        String content = DiscordSanitizer.normalize(message.content());
        if (content.isBlank()) {
            return;
        }
        inboundChatPublisher.publish("Discord", author, content);
    }

    private void deliverToDiscord(DiscordOutboundMessage message) throws Exception {
        long startedNanos = System.nanoTime();
        Exception lastFailure = null;
        if (!delivery.ready(message)) {
            awaitTransportReady(message);
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
                    if (!delivery.ready(message)) {
                        awaitTransportReady(message);
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

    private void awaitTransportReady(DiscordOutboundMessage message) throws InterruptedException {
        long waitSeconds = message.preferWebhook()
                ? settings.deliveryTimeout().plusSeconds(5).toSeconds()
                : settings.reconnectMaxDelaySeconds();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(waitSeconds);
        while (running.get() && !delivery.ready(message) && System.nanoTime() < deadline) {
            Thread.sleep(250L);
        }
        if (!delivery.ready(message)) {
            throw new IllegalStateException(message.preferWebhook()
                    ? "Discord chat webhook is unavailable"
                    : "Discord gateway did not reconnect before the internal deadline");
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
            DiscordEmbedMessage embed = template.render(values);
            String player = values.getOrDefault("player", "");
            if (!player.isBlank()) {
                embed = embed.withThumbnail(playerAvatar(player));
            }
            outbound = DiscordOutboundMessage.announcement(embed);
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
            String player = eventValue(playerName, 80);
            return settings.linkSuccessEmbed().render(java.util.Map.of("player", player))
                    .withThumbnail(playerAvatar(player));
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

    private static String playerAvatar(String playerName) {
        return "https://mc-heads.net/avatar/"
                + URLEncoder.encode(playerName, StandardCharsets.UTF_8)
                + "/128";
    }

    CommunicationsMetrics.Snapshot diagnostics(String storageBackend) {
        return metrics.snapshot(
                isRunning() ? "running" : closed.get() ? "closed" : "degraded",
                gateway.runtimeState(),
                gateway.slashState(),
                webhookProvisioner == null ? "unavailable" : webhookProvisioner.chatTransportState(),
                storageBackend,
                inboundQueue.size(),
                outboundQueue.size(),
                links.queueDepth(),
                gateway.reconnectCount()
        );
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

    void reconcileLinkedRole(UUID playerUuid) {
        links.reconcileRole(playerUuid);
    }

    ResourceState resourceState() {
        return new ResourceState(links.codeCount(), links.linkCount());
    }

    record ResourceState(int linkCodes, int linkIndexEntries) {
        static ResourceState empty() {
            return new ResourceState(0, 0);
        }
    }

    @FunctionalInterface
    interface InboundChatPublisher {
        void publish(String source, String author, String content);
    }
}
