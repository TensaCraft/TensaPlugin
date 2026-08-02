package ua.co.tensa.modules.discord;

import ua.co.tensa.Message;
import ua.co.tensa.modules.chat.ProxyChatMessage;

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

final class DiscordRuntime implements AutoCloseable {
    private final DiscordSettings settings;
    private final DiscordGateway gateway;
    private final DiscordLinkService links;
    private final DiscordDelivery delivery;
    private final InboundChatPublisher inboundChatPublisher;
    private final DiscordServerPolicy serverPolicy;
    private final DiscordEventFormatter eventFormatter;
    private final DiscordEventRateLimiter eventRateLimiter;
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
        this(settings, gateway, links, delivery, (source, author, content) -> { });
    }

    DiscordRuntime(
            DiscordSettings settings,
            DiscordGateway gateway,
            DiscordLinkService links,
            DiscordDelivery delivery,
            InboundChatPublisher inboundChatPublisher
    ) {
        this.settings = settings;
        this.gateway = gateway;
        this.links = links;
        this.delivery = delivery;
        this.inboundChatPublisher = inboundChatPublisher;
        this.serverPolicy = new DiscordServerPolicy(settings);
        this.eventFormatter = new DiscordEventFormatter(settings, serverPolicy);
        this.eventRateLimiter = new DiscordEventRateLimiter(settings.eventRatePerMinute());
        this.inboundQueue = new BoundedWorkerQueue<>(
                "tensa-discord-inbound",
                settings.queueCapacity(),
                this::deliverToMinecraft,
                (ignored, error) -> warnRateLimited("Discord to Minecraft relay failed")
        );
        this.outboundQueue = new BoundedWorkerQueue<>(
                "tensa-discord-outbound",
                settings.queueCapacity(),
                this::deliverToDiscord,
                (ignored, error) -> warnRateLimited("Minecraft to Discord relay failed after bounded retries")
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
            gateway.start(this::acceptDiscordMessage, this::completeLinkFromDiscord, links::reconcileRoles);
        } catch (RuntimeException e) {
            close();
            throw new IllegalStateException("Discord gateway could not be started");
        }
    }

    boolean relayMinecraftChat(ProxyChatMessage message) {
        if (!running.get()
                || !settings.minecraftToDiscord()
                || message == null
                || message.origin() != ProxyChatMessage.Origin.MINECRAFT
                || !"global".equalsIgnoreCase(message.channel())
                || message.playerUuid() == null) {
            return false;
        }
        String player = DiscordSanitizer.forDiscord(message.playerName(), 80);
        String content = DiscordSanitizer.forDiscord(message.message(), settings.maxDiscordMessageLength());
        if (content.isBlank()) {
            return false;
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
            warnRateLimited("Discord outbound relay queue is full; dropping a new message");
        }
        return accepted;
    }

    void announceJoin(String playerName, String serverName) {
        if (settings.joinMessages()) {
            announce("Гравець приєднався", eventFormatter.join(playerName, serverName), DiscordEmbedMessage.GREEN);
        }
    }

    void announceQuit(String playerName, String serverName) {
        if (settings.quitMessages()) {
            announce("Гравець вийшов", eventFormatter.quit(playerName, serverName), DiscordEmbedMessage.GRAY);
        }
    }

    void announceServerSwitch(String playerName, String fromServer, String toServer) {
        if (settings.serverSwitchMessages()) {
            announce("Перехід між серверами", eventFormatter.switchServer(playerName, fromServer, toServer), DiscordEmbedMessage.BLURPLE);
        }
    }

    void announceBackendUnavailable(String serverName) {
        if (settings.backendStatusMessages()) {
            announce("Сервер недоступний", eventFormatter.backendUnavailable(serverName), DiscordEmbedMessage.RED);
        }
    }

    void announceBackendRecovered(String serverName) {
        if (settings.backendStatusMessages()) {
            announce("Сервер знову доступний", eventFormatter.backendRecovered(serverName), DiscordEmbedMessage.GREEN);
        }
    }

    void announceAdvancement(String playerName, String serverName, String advancement) {
        if (settings.advancementMessages()) {
            announce("Нове досягнення", eventFormatter.advancement(playerName, serverName, advancement), DiscordEmbedMessage.YELLOW);
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
        Exception lastFailure = null;
        if (delivery.requiresReadyGateway(message)) {
            awaitGatewayReconnect(message.route());
        }
        for (int attempt = 1; attempt <= settings.deliveryAttempts(); attempt++) {
            try {
                delivery.send(message).get(settings.deliveryTimeout().toMillis(), TimeUnit.MILLISECONDS);
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            } catch (Exception e) {
                lastFailure = e;
                if (attempt < settings.deliveryAttempts()) {
                    if (!gateway.isReady(message.route())) {
                        awaitGatewayReconnect(message.route());
                    }
                    Thread.sleep(Math.min(2_000L, 250L << (attempt - 1)));
                }
            }
        }
        throw lastFailure == null ? new IllegalStateException("Discord delivery failed") : lastFailure;
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
                .exceptionally(ignored -> DiscordEmbedMessage.linkError(
                        "Сервіс прив'язки зараз перевантажений. Спробуйте пізніше."
                ));
    }

    private DiscordEmbedMessage slashReply(DiscordLinkService.Result result) {
        return switch (result.type()) {
            case LINKED -> DiscordEmbedMessage.success(
                    "Акаунт успішно прив'язано до " + result.account().playerName() + "."
            );
            case INVALID_CODE -> DiscordEmbedMessage.linkError(
                    "Код недійсний або вже використаний. Створіть новий через /discord link у Minecraft."
            );
            case EXPIRED_CODE -> DiscordEmbedMessage.linkError(
                    "Термін дії коду минув. Створіть новий через /discord link у Minecraft."
            );
            case PLAYER_ALREADY_LINKED -> DiscordEmbedMessage.linkError(
                    "Цей Minecraft-акаунт уже прив'язаний."
            );
            case DISCORD_ALREADY_LINKED -> DiscordEmbedMessage.linkError(
                    "Цей Discord-акаунт уже прив'язаний до іншого гравця."
            );
            case ROLE_FAILED -> DiscordEmbedMessage.linkError(
                    "Не вдалося видати роль. Прив'язку не збережено; зверніться до адміністратора."
            );
            case BUSY -> DiscordEmbedMessage.linkError(
                    "Сервіс прив'язки зараз перевантажений. Спробуйте пізніше."
            );
            default -> DiscordEmbedMessage.linkError(
                    "Не вдалося зберегти прив'язку. Спробуйте ще раз пізніше."
            );
        };
    }

    private void announce(String title, String content, int color) {
        if (!running.get() || !eventRateLimiter.tryAcquire(Instant.now())) {
            return;
        }
        if (!content.isBlank() && !outboundQueue.offer(DiscordOutboundMessage.announcement(
                DiscordEmbedMessage.of(title, content, color)
        ))) {
            warnRateLimited("Discord outbound relay queue is full; dropping an announcement");
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

    @FunctionalInterface
    interface InboundChatPublisher {
        void publish(String source, String author, String content);
    }
}
