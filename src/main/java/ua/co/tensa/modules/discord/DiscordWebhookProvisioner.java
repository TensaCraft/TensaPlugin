package ua.co.tensa.modules.discord;

import ua.co.tensa.Message;
import ua.co.tensa.modules.runtime.ModuleScheduler;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Fetches or creates bot-owned webhooks without ever persisting their credentials. */
final class DiscordWebhookProvisioner implements AutoCloseable {
    private static final String SCOPE = "discord-webhook-provisioning";
    private final DiscordSettings settings;
    private final DiscordGateway gateway;
    private final DiscordWebhookClient client;
    private final DiscordWebhookBindingRepository repository;
    private final ModuleScheduler scheduler;
    private final CommunicationsMetrics metrics;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Map<DiscordRoute, AtomicBoolean> requested = new EnumMap<>(DiscordRoute.class);

    DiscordWebhookProvisioner(
            DiscordSettings settings,
            DiscordGateway gateway,
            DiscordWebhookClient client,
            DiscordWebhookBindingRepository repository,
            ModuleScheduler scheduler,
            CommunicationsMetrics metrics
    ) {
        this.settings = settings;
        this.gateway = gateway;
        this.client = client;
        this.repository = repository;
        this.scheduler = scheduler;
        this.metrics = metrics;
        for (DiscordRoute route : DiscordRoute.values()) {
            requested.put(route, new AtomicBoolean());
        }
        client.onInvalidated(this::recover);
    }

    void refreshAll() {
        if (!settings.webhookAutoCreate() || closed.get()) {
            return;
        }
        if (settings.minecraftToDiscord() && client.requiresManaged(DiscordRoute.CHAT)) {
            recover(DiscordRoute.CHAT);
        }
        if (eventsEnabled() && client.requiresManaged(DiscordRoute.EVENTS)) {
            recover(DiscordRoute.EVENTS);
        }
    }

    private void recover(DiscordRoute route) {
        if (!settings.webhookAutoCreate() || closed.get() || !requested.get(route).compareAndSet(false, true)) {
            return;
        }
        try {
            scheduler.schedule(ModuleScheduler.job("discord-webhook-" + route.name().toLowerCase(), () -> provision(route))
                    .scope(SCOPE)
                    .dedupe("discord-webhook-" + route.name())
                    .attempts(3)
                    .timeout(settings.deliveryTimeout().plusSeconds(3))
                    .backoff(Duration.ofSeconds(1), Duration.ofSeconds(30))
                    .jitter(0.2)
                    .onDeadLetter(error -> {
                        requested.get(route).set(false);
                        metrics.recordFailure(error);
                        Message.warn("communications component=webhook route=" + route.name().toLowerCase()
                                + " transition=bot-fallback failure="
                                + DiscordDiagnostics.unwrap(error).getClass().getSimpleName());
                    })
                    .build());
        } catch (IllegalStateException | RejectedExecutionException busy) {
            requested.get(route).set(false);
            metrics.recordFailure(busy);
        }
    }

    private void provision(DiscordRoute route) throws Exception {
        String preferredId = repository.find(route).map(DiscordWebhookBinding::webhookId).orElse("");
        String name = route == DiscordRoute.EVENTS
                ? DiscordSanitizer.truncate(settings.webhookName() + " Events", 80)
                : settings.webhookName();
        ManagedDiscordWebhook webhook;
        try {
            webhook = gateway.ensureManagedWebhook(route, name, preferredId)
                    .get(settings.deliveryTimeout().plusSeconds(2).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception failure) {
            Throwable cause = DiscordDiagnostics.unwrap(failure);
            if (permanentPermissionFailure(cause)) {
                requested.get(route).set(false);
                metrics.recordFailure(cause);
                Message.warn("communications component=webhook route=" + route.name().toLowerCase()
                        + " transition=bot-fallback failure=" + cause.getClass().getSimpleName());
                return;
            }
            throw failure;
        }
        repository.remember(webhook);
        client.bind(webhook);
        requested.get(route).set(false);
        Message.info("communications component=webhook route=" + route.name().toLowerCase()
                + " transition=ready managed=true");
    }

    private boolean eventsEnabled() {
        return settings.joinMessages()
                || settings.quitMessages()
                || settings.serverSwitchMessages()
                || settings.backendStatusMessages()
                || settings.advancementMessages();
    }

    private static boolean permanentPermissionFailure(Throwable cause) {
        return cause instanceof SecurityException
                || cause instanceof net.dv8tion.jda.api.exceptions.InsufficientPermissionException
                || cause instanceof net.dv8tion.jda.api.exceptions.ErrorResponseException response
                && response.getErrorCode() == 50_013;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        scheduler.cancelScope(SCOPE);
        requested.values().forEach(value -> value.set(false));
        repository.close();
    }
}
