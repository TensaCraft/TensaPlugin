package ua.co.tensa.modules.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.SessionDisconnectEvent;
import net.dv8tion.jda.api.events.session.SessionRecreateEvent;
import net.dv8tion.jda.api.events.session.SessionResumeEvent;
import net.dv8tion.jda.api.events.session.ShutdownEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import ua.co.tensa.Message;

import java.time.Duration;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import ua.co.tensa.modules.runtime.ModuleScheduler;

final class JdaDiscordGateway extends ListenerAdapter implements DiscordGateway {
    private final DiscordSettings settings;
    private final DiscordSlashCommandRegistrar slashCommandRegistrar;
    private final AtomicBoolean ready = new AtomicBoolean();
    private final AtomicBoolean slashCommandReady = new AtomicBoolean();
    private final AtomicBoolean acceptingEvents = new AtomicBoolean();
    private final AtomicLong lastInteractionWarning = new AtomicLong();
    private final AtomicLong reconnects = new AtomicLong();
    private final AtomicInteger pendingReplyDeletions = new AtomicInteger();
    private final AtomicReference<String> runtimeState = new AtomicReference<>("new");
    private final AtomicReference<String> slashState = new AtomicReference<>("unavailable");
    private final ModuleScheduler scheduler;
    private final boolean ownsScheduler;
    private volatile JDA jda;
    private volatile Guild guild;
    private volatile TextChannel channel;
    private volatile TextChannel eventsChannel;
    private volatile Consumer<DiscordInboundMessage> inboundHandler = ignored -> { };
    private volatile DiscordSlashInteractionHandler slashInteractionHandler;
    private volatile DiscordBotLease botLease;
    private volatile Runnable readyHandler = () -> { };

    JdaDiscordGateway(DiscordSettings settings) {
        this(settings, new ModuleScheduler("tensa-discord", new ModuleScheduler.Defaults(
                settings.eventStateCapacity(), 1, settings.queueCapacity(), Duration.ofSeconds(10), 1,
                Duration.ofMillis(250), Duration.ofSeconds(5), 0.1
        )), true);
    }

    JdaDiscordGateway(DiscordSettings settings, ModuleScheduler scheduler) {
        this(settings, scheduler, false);
    }

    private JdaDiscordGateway(DiscordSettings settings, ModuleScheduler scheduler, boolean ownsScheduler) {
        this.settings = settings;
        this.slashCommandRegistrar = new DiscordSlashCommandRegistrar(settings.linkCommandName());
        this.scheduler = java.util.Objects.requireNonNull(scheduler, "scheduler");
        this.ownsScheduler = ownsScheduler;
    }

    @Override
    public synchronized void start(
            Consumer<DiscordInboundMessage> inboundHandler,
            SlashLinkHandler slashLinkHandler,
            Runnable readyHandler
    ) {
        if (jda != null) {
            return;
        }
        DiscordBotLease lease = DiscordBotLease.acquire(settings.credentials().botToken(), this);
        DiscordSlashInteractionHandler interactionHandler = new DiscordSlashInteractionHandler(
                settings.linkCommandName(),
                settings.guildId(),
                slashLinkHandler
        );
        this.inboundHandler = inboundHandler;
        this.slashInteractionHandler = interactionHandler;
        this.readyHandler = readyHandler;
        this.botLease = lease;
        acceptingEvents.set(true);
        runtimeState.set("connecting");
        slashState.set("registering");
        try {
            jda = JDABuilder.createLight(
                            settings.credentials().botToken(),
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT
                    )
                    .setAutoReconnect(true)
                    .setMaxReconnectDelay(settings.reconnectMaxDelaySeconds())
                    .setEnableShutdownHook(false)
                    .setBulkDeleteSplittingEnabled(false)
                    .addEventListeners(this)
                    .build();
        } catch (RuntimeException error) {
            acceptingEvents.set(false);
            this.slashInteractionHandler = null;
            this.botLease = null;
            interactionHandler.close();
            lease.close();
            throw error;
        }
    }

    @Override
    public void onReady(ReadyEvent event) {
        activate(event.getJDA());
    }

    @Override
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        if (isCurrentConnection(event.getJDA())) {
            deactivate();
        }
    }

    @Override
    public void onSessionResume(SessionResumeEvent event) {
        reconnects.incrementAndGet();
        activate(event.getJDA());
    }

    @Override
    public void onSessionRecreate(SessionRecreateEvent event) {
        reconnects.incrementAndGet();
        activate(event.getJDA());
    }

    private void activate(JDA connectedJda) {
        if (!isCurrentConnection(connectedJda)) {
            return;
        }
        Guild configuredGuild = connectedJda.getGuildById(settings.guildId());
        TextChannel configuredChannel = connectedJda.getTextChannelById(settings.channelId());
        TextChannel configuredEventsChannel = connectedJda.getTextChannelById(settings.eventsChannelId());
        if (configuredGuild == null) {
            ready.set(false);
            runtimeState.set("degraded");
            slashState.set("unavailable");
            Message.warn("Discord gateway connected, but the configured guild is unavailable; linking command cannot be registered");
            return;
        }
        this.guild = configuredGuild;
        if (configuredChannel != null && configuredChannel.getGuild().getId().equals(settings.guildId())) {
            this.channel = configuredChannel;
        } else {
            this.channel = null;
            Message.warn("Discord gateway connected, but the configured relay channel is unavailable");
        }
        if (configuredEventsChannel != null && configuredEventsChannel.getGuild().getId().equals(settings.guildId())) {
            this.eventsChannel = configuredEventsChannel;
        } else {
            this.eventsChannel = null;
            Message.warn("Discord gateway connected, but the configured events channel is unavailable");
        }
        ready.set(true);
        slashCommandReady.set(false);
        runtimeState.set("ready");
        slashState.set("registering");
        slashCommandRegistrar.ensureRegistered(new JdaGuildCommands(configuredGuild))
                .whenComplete((result, error) -> {
                    if (!isCurrentConnection(connectedJda)) {
                        return;
                    }
                    if (error != null) {
                        slashCommandReady.set(false);
                        slashState.set("failed");
                        Message.warn("Discord guild command /" + settings.linkCommandName()
                                + " is unavailable: " + DiscordDiagnostics.describe(error));
                        return;
                    }
                    slashCommandReady.set(true);
                    slashState.set("ready");
                    String action = result.changed() ? "registered and verified" : "verified";
                    Message.info("Discord guild command /" + settings.linkCommandName() + " " + action);
                });
        readyHandler.run();
        Message.info("Discord gateway connected for the configured guild");
    }

    @Override
    public void onShutdown(ShutdownEvent event) {
        if (isCurrentConnection(event.getJDA())) {
            deactivate();
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!isCurrentConnection(event.getJDA())) {
            return;
        }
        String guildId = event.isFromGuild() ? event.getGuild().getId() : "";
        Member member = event.getMember();
        String authorName = member == null ? event.getAuthor().getEffectiveName() : member.getEffectiveName();
        inboundHandler.accept(new DiscordInboundMessage(
                guildId,
                event.getChannel().getId(),
                event.getAuthor().getId(),
                authorName,
                event.getMessage().getContentDisplay(),
                event.getAuthor().isBot(),
                event.isWebhookMessage(),
                event.getMessageId()
        ));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!isCurrentConnection(event.getJDA())) {
            return;
        }
        DiscordSlashInteractionHandler handler = slashInteractionHandler;
        if (handler == null) {
            return;
        }
        handler.handle(new JdaSlashInteraction(event)).whenComplete((handled, error) -> {
            if (error != null) {
                warnInteractionFailure(error);
            }
        });
    }

    @Override
    public CompletableFuture<Void> sendBotMessage(DiscordRoute route, String content) {
        TextChannel target = route == DiscordRoute.EVENTS ? eventsChannel : channel;
        if (!ready.get() || target == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Discord gateway is not ready"));
        }
        return target.sendMessage(content)
                .setAllowedMentions(List.of())
                .submit()
                .thenApply(ignored -> null);
    }

    @Override
    public CompletableFuture<Void> sendBotEmbed(DiscordRoute route, DiscordEmbedMessage embed) {
        TextChannel target = route == DiscordRoute.EVENTS ? eventsChannel : channel;
        if (!ready.get() || target == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Discord gateway is not ready"));
        }
        return target.sendMessageEmbeds(toMessageEmbed(embed))
                .setAllowedMentions(List.of())
                .submit()
                .thenApply(ignored -> null);
    }

    @Override
    public CompletableFuture<Void> assignLinkedRole(String discordUserId) {
        if (settings.linkedRoleId().isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        return updateRole(discordUserId, true);
    }

    @Override
    public CompletableFuture<Void> removeLinkedRole(String discordUserId) {
        if (settings.linkedRoleId().isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        return updateRole(discordUserId, false);
    }

    @Override
    public CompletableFuture<Void> updateNickname(String discordUserId, String nickname) {
        Guild configuredGuild = guild;
        if (!ready.get() || configuredGuild == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Discord gateway is not ready"));
        }
        String safeNickname = DiscordSanitizer.truncate(DiscordSanitizer.normalize(nickname), 32).trim();
        if (safeNickname.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Discord nickname is empty"));
        }
        return configuredGuild.retrieveMemberById(discordUserId)
                .submit()
                .thenCompose(member -> configuredGuild.modifyNickname(member, safeNickname).submit());
    }

    @Override
    public CompletableFuture<Void> sendTemporaryReply(
            String channelId,
            String messageId,
            String content,
            Duration deleteAfter
    ) {
        TextChannel target = channel;
        if (!ready.get() || target == null || !target.getId().equals(channelId)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Discord relay channel is not ready"));
        }
        var action = target.sendMessage(content).setAllowedMentions(List.of());
        if (messageId != null && !messageId.isBlank()) {
            action = action.setMessageReference(messageId).failOnInvalidReply(false);
        }
        return action.submit().thenAccept(reply -> {
            if (pendingReplyDeletions.incrementAndGet() > settings.eventStateCapacity()) {
                pendingReplyDeletions.decrementAndGet();
                reply.delete().queue(null, ignored -> { });
                return;
            }
            try {
                scheduler.schedule(ModuleScheduler.job("discord-reply-delete-" + reply.getId(), () -> {
                            try {
                                if (acceptingEvents.get()) {
                                    reply.delete().queue(null, ignored -> { });
                                }
                            } finally {
                                pendingReplyDeletions.decrementAndGet();
                            }
                        })
                        .scope("discord-reply-delete")
                        .dedupe("discord-reply-delete-" + reply.getId())
                        .delay(deleteAfter.isNegative() || deleteAfter.isZero() ? Duration.ofMillis(1) : deleteAfter)
                        .build());
            } catch (RuntimeException schedulerBusy) {
                pendingReplyDeletions.decrementAndGet();
                reply.delete().queue(null, ignored -> { });
            }
        });
    }

    @Override
    public CompletableFuture<ManagedDiscordWebhook> ensureManagedWebhook(
            DiscordRoute route,
            String name,
            String preferredWebhookId
    ) {
        TextChannel target = route == DiscordRoute.EVENTS ? eventsChannel : channel;
        Guild configuredGuild = guild;
        if (!ready.get() || target == null || configuredGuild == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Discord webhook channel is not ready"));
        }
        if (!configuredGuild.getSelfMember().hasPermission(target, Permission.MANAGE_WEBHOOKS)) {
            return CompletableFuture.failedFuture(new SecurityException(
                    "Discord bot lacks Manage Webhooks permission in the configured channel"));
        }
        return target.retrieveWebhooks().submit().thenCompose(webhooks -> {
            Webhook existing = webhooks.stream()
                    .filter(webhook -> ownedByCurrentBot(webhook, configuredGuild))
                    .filter(webhook -> webhook.getToken() != null && !webhook.getToken().isBlank())
                    .filter(webhook -> !preferredWebhookId.isBlank() && webhook.getId().equals(preferredWebhookId)
                            || webhook.getName().equals(name))
                    .findFirst()
                    .orElse(null);
            if (existing != null) {
                return CompletableFuture.completedFuture(managedWebhook(route, existing));
            }
            return target.createWebhook(name).submit().thenApply(webhook -> managedWebhook(route, webhook));
        });
    }

    @Override
    public boolean isReady() {
        return ready.get();
    }

    @Override
    public boolean isReady(DiscordRoute route) {
        return ready.get() && (route == DiscordRoute.EVENTS ? eventsChannel != null : channel != null);
    }

    @Override
    public String selfUserId() {
        JDA current = jda;
        return current == null ? "" : current.getSelfUser().getId();
    }

    @Override
    public String runtimeState() {
        return runtimeState.get();
    }

    @Override
    public String slashState() {
        return slashState.get();
    }

    @Override
    public long reconnectCount() {
        return reconnects.get();
    }

    @Override
    public synchronized void close(Duration timeout) {
        acceptingEvents.set(false);
        runtimeState.set("closed");
        slashState.set("closed");
        deactivate();
        JDA current = jda;
        jda = null;
        DiscordSlashInteractionHandler interactionHandler = slashInteractionHandler;
        slashInteractionHandler = null;
        if (interactionHandler != null) {
            interactionHandler.close();
        }
        inboundHandler = ignored -> { };
        readyHandler = () -> { };
        scheduler.cancelScope("discord-reply-delete");
        if (ownsScheduler) {
            scheduler.close();
        }
        DiscordBotLease lease = botLease;
        botLease = null;
        try {
            if (current != null) {
                current.removeEventListener(this);
                current.shutdown();
                if (!current.awaitShutdown(timeout)) {
                    current.shutdownNow();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (current != null) {
                current.shutdownNow();
            }
        } finally {
            if (lease != null) {
                lease.close();
            }
        }
    }

    private CompletableFuture<Void> updateRole(String discordUserId, boolean assign) {
        Guild configuredGuild = guild;
        if (!ready.get() || configuredGuild == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Discord gateway is not ready"));
        }
        Role role = configuredGuild.getRoleById(settings.linkedRoleId());
        if (role == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Configured Discord linked role is unavailable"));
        }
        return configuredGuild.retrieveMemberById(discordUserId)
                .submit()
                .thenCompose(member -> assign
                        ? configuredGuild.addRoleToMember(member, role).submit()
                        : configuredGuild.removeRoleFromMember(member, role).submit());
    }

    private boolean ownedByCurrentBot(Webhook webhook, Guild configuredGuild) {
        try {
            return webhook.getOwnerAsUser() != null
                    && webhook.getOwnerAsUser().getId().equals(configuredGuild.getJDA().getSelfUser().getId());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static ManagedDiscordWebhook managedWebhook(DiscordRoute route, Webhook webhook) {
        return ManagedDiscordWebhook.from(route, webhook.getId(), URI.create(webhook.getUrl()));
    }

    private boolean isCurrentConnection(JDA candidate) {
        return acceptingEvents.get() && candidate != null && candidate == jda;
    }

    private void deactivate() {
        ready.set(false);
        slashCommandReady.set(false);
        if (acceptingEvents.get()) {
            runtimeState.set("reconnecting");
            slashState.set("unavailable");
        }
        guild = null;
        channel = null;
        eventsChannel = null;
    }

    private void warnInteractionFailure(Throwable error) {
        long now = System.nanoTime();
        long previous = lastInteractionWarning.get();
        if (previous != 0L && now - previous < java.util.concurrent.TimeUnit.SECONDS.toNanos(30)) {
            return;
        }
        if (lastInteractionWarning.compareAndSet(previous, now)) {
            Message.warn("Discord slash command /" + settings.linkCommandName()
                    + " could not be acknowledged or completed: " + DiscordDiagnostics.describe(error)
                    + ". Check for another active bot process if Discord reports error 10062");
        }
    }

    private static final class JdaSlashInteraction implements DiscordSlashInteractionHandler.Interaction {
        private final SlashCommandInteractionEvent event;

        private JdaSlashInteraction(SlashCommandInteractionEvent event) {
            this.event = event;
        }

        @Override
        public String commandName() {
            return event.getName();
        }

        @Override
        public String guildId() {
            return event.isFromGuild() && event.getGuild() != null ? event.getGuild().getId() : "";
        }

        @Override
        public String code() {
            return event.getOption("code", "", option -> option.getAsString());
        }

        @Override
        public String userId() {
            return event.getUser().getId();
        }

        @Override
        public String userName() {
            return event.getMember() == null
                    ? event.getUser().getEffectiveName()
                    : event.getMember().getEffectiveName();
        }

        @Override
        public CompletableFuture<DiscordSlashInteractionHandler.DeferredReply> deferEphemeral() {
            return event.deferReply(true).submit().thenApply(hook -> message ->
                    hook.editOriginalEmbeds(toMessageEmbed(message))
                            .submit()
                            .thenApply(ignored -> null));
        }
    }

    private static MessageEmbed toMessageEmbed(DiscordEmbedMessage embed) {
        EmbedBuilder builder = new EmbedBuilder()
                .setTitle(embed.title())
                .setDescription(embed.description())
                .setColor(embed.color())
                .setTimestamp(embed.timestamp());
        if (!embed.thumbnailUrl().isBlank()) {
            builder.setThumbnail(embed.thumbnailUrl());
        }
        if (!embed.footer().isBlank()) {
            builder.setFooter(embed.footer());
        }
        if (!embed.imageUrl().isBlank()) {
            builder.setImage(embed.imageUrl());
        }
        return builder.build();
    }
}
