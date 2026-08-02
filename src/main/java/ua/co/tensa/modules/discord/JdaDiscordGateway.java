package ua.co.tensa.modules.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

final class JdaDiscordGateway extends ListenerAdapter implements DiscordGateway {
    private final DiscordSettings settings;
    private final DiscordSlashCommandRegistrar slashCommandRegistrar;
    private final AtomicBoolean ready = new AtomicBoolean();
    private final AtomicBoolean slashCommandReady = new AtomicBoolean();
    private final AtomicBoolean acceptingEvents = new AtomicBoolean();
    private final AtomicLong lastInteractionWarning = new AtomicLong();
    private volatile JDA jda;
    private volatile Guild guild;
    private volatile TextChannel channel;
    private volatile TextChannel eventsChannel;
    private volatile Consumer<DiscordInboundMessage> inboundHandler = ignored -> { };
    private volatile DiscordSlashInteractionHandler slashInteractionHandler;
    private volatile DiscordBotLease botLease;
    private volatile Runnable readyHandler = () -> { };

    JdaDiscordGateway(DiscordSettings settings) {
        this.settings = settings;
        this.slashCommandRegistrar = new DiscordSlashCommandRegistrar(settings.linkCommandName());
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
        activate(event.getJDA());
    }

    @Override
    public void onSessionRecreate(SessionRecreateEvent event) {
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
        slashCommandRegistrar.ensureRegistered(new JdaGuildCommands(configuredGuild))
                .whenComplete((result, error) -> {
                    if (!isCurrentConnection(connectedJda)) {
                        return;
                    }
                    if (error != null) {
                        slashCommandReady.set(false);
                        Message.warn("Discord guild command /" + settings.linkCommandName()
                                + " is unavailable: " + DiscordDiagnostics.describe(error));
                        return;
                    }
                    slashCommandReady.set(true);
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
                event.isWebhookMessage()
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
    public synchronized void close(Duration timeout) {
        acceptingEvents.set(false);
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

    private boolean isCurrentConnection(JDA candidate) {
        return acceptingEvents.get() && candidate != null && candidate == jda;
    }

    private void deactivate() {
        ready.set(false);
        slashCommandReady.set(false);
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
        return new EmbedBuilder()
                .setTitle(embed.title())
                .setDescription(embed.description())
                .setColor(embed.color())
                .setTimestamp(embed.timestamp())
                .build();
    }
}
