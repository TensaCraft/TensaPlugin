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
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import ua.co.tensa.Message;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

final class JdaDiscordGateway extends ListenerAdapter implements DiscordGateway {
    private static final String COMMAND_MARKER = "TensaPlugin";

    private final DiscordSettings settings;
    private final AtomicBoolean ready = new AtomicBoolean();
    private volatile JDA jda;
    private volatile Guild guild;
    private volatile TextChannel channel;
    private volatile TextChannel eventsChannel;
    private volatile Consumer<DiscordInboundMessage> inboundHandler = ignored -> { };
    private volatile SlashLinkHandler slashLinkHandler = (code, id, name) -> CompletableFuture.completedFuture(
            DiscordEmbedMessage.linkError("Сервіс прив'язки недоступний.")
    );
    private volatile Runnable readyHandler = () -> { };

    JdaDiscordGateway(DiscordSettings settings) {
        this.settings = settings;
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
        this.inboundHandler = inboundHandler;
        this.slashLinkHandler = slashLinkHandler;
        this.readyHandler = readyHandler;
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
    }

    @Override
    public void onReady(ReadyEvent event) {
        activate(event.getJDA());
    }

    @Override
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        ready.set(false);
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
        Guild configuredGuild = connectedJda.getGuildById(settings.guildId());
        TextChannel configuredChannel = connectedJda.getTextChannelById(settings.channelId());
        TextChannel configuredEventsChannel = connectedJda.getTextChannelById(settings.eventsChannelId());
        if (configuredGuild == null
                || configuredChannel == null
                || !configuredChannel.getGuild().getId().equals(settings.guildId())) {
            ready.set(false);
            Message.warn("Discord gateway connected, but the configured guild/channel is unavailable");
            return;
        }
        this.guild = configuredGuild;
        this.channel = configuredChannel;
        if (configuredEventsChannel != null && configuredEventsChannel.getGuild().getId().equals(settings.guildId())) {
            this.eventsChannel = configuredEventsChannel;
        } else {
            this.eventsChannel = null;
            Message.warn("Discord gateway connected, but the configured events channel is unavailable");
        }
        ready.set(true);
        registerSlashCommand(configuredGuild);
        readyHandler.run();
        Message.info("Discord gateway connected for the configured guild/channel");
    }

    @Override
    public void onShutdown(ShutdownEvent event) {
        ready.set(false);
        guild = null;
        channel = null;
        eventsChannel = null;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
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
        if (!event.getName().equals(settings.linkCommandName())) {
            return;
        }
        if (!event.isFromGuild() || event.getGuild() == null || !event.getGuild().getId().equals(settings.guildId())) {
            event.replyEmbeds(toMessageEmbed(DiscordEmbedMessage.linkError(
                            "Ця команда доступна лише на налаштованому Discord-сервері."
                    )))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        String code = event.getOption("code", "", option -> option.getAsString());
        String userName = event.getMember() == null
                ? event.getUser().getEffectiveName()
                : event.getMember().getEffectiveName();
        event.deferReply(true).queue(
                hook -> slashLinkHandler
                        .link(code, event.getUser().getId(), userName)
                        .exceptionally(ignored -> DiscordEmbedMessage.linkError(
                                "Не вдалося завершити прив'язку. Спробуйте ще раз пізніше."
                        ))
                        .thenAccept(reply -> hook.editOriginalEmbeds(toMessageEmbed(reply)).queue()),
                ignored -> Message.warn("Discord slash command acknowledgement failed; verify that no second bot process handles /"
                        + settings.linkCommandName())
        );
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
        ready.set(false);
        JDA current = jda;
        jda = null;
        guild = null;
        channel = null;
        eventsChannel = null;
        if (current == null) {
            return;
        }
        current.shutdown();
        try {
            if (!current.awaitShutdown(timeout)) {
                current.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            current.shutdownNow();
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

    private void registerSlashCommand(Guild configuredGuild) {
        configuredGuild.retrieveCommands().queue(commands -> commands.stream()
                .filter(command -> command.getDescription().contains(COMMAND_MARKER))
                .filter(command -> !command.getName().equals(settings.linkCommandName()))
                .forEach(command -> command.delete().queue()));

        configuredGuild.upsertCommand(Commands.slash(
                        settings.linkCommandName(),
                        "Прив'язати Minecraft-акаунт через " + COMMAND_MARKER
                ).addOption(OptionType.STRING, "code", "Одноразовий код із команди /discord link", true))
                .queue(
                        ignored -> Message.info("Discord slash command registered: /" + settings.linkCommandName()),
                        ignored -> Message.warn("Discord slash command registration failed")
                );
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
