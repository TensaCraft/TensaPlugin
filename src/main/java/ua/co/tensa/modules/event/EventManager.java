package ua.co.tensa.modules.event;

import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.network.ListenerType;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import ua.co.tensa.Util;
import ua.co.tensa.core.user.UserDataService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static ua.co.tensa.modules.event.EventsModule.Events.*;

public class EventManager {
    private static final String EMPTY = "";
    private static final List<String> SUPPORTED_PLACEHOLDERS = List.of(
            "event",
            "player",
            "uuid",
            "server",
            "fromServer",
            "toServer",
            "originalServer",
            "initialServer",
            "ip",
            "host",
            "port",
            "address",
            "protocol",
            "intent",
            "listener",
            "firstJoinAt"
    );
    private static final String DELAY = "[delay]";
    private static final String CONSOLE = "[console]";

    public static synchronized void initialise(Path pluginPath) {
        reload();
    }

    public static synchronized void reload() {
        ua.co.tensa.modules.event.data.EventsConfig.get().reloadCfg();
    }

    public static synchronized void shutdown() {
    }

    private static void sendCommand(EventContext context, String command, boolean console) {
        if (console || context.player() == null) {
            Util.executeCommand(command);
        } else {
            Util.executeCommand(context.player(), command);
        }
    }

    static List<String> renderCommands(List<String> commands, Map<String, String> placeholders) {
        List<String> out = new ArrayList<>();
        if (commands == null || commands.isEmpty()) {
            return out;
        }
        Map<String, String> context = placeholders == null ? Map.of() : placeholders;
        for (String command : commands) {
            String template = command == null ? EMPTY : command;
            out.add(Message.renderTemplateString(template, context));
        }
        return out;
    }

    private static void execute(EventsModule.Events event, EventContext context) {
        if (!event.enabled()) {
            return;
        }

        AtomicLong delayAccum = new AtomicLong(0);
        for (String command : renderCommands(event.commands(), context.placeholders())) {
            String trimmed = command == null ? EMPTY : command.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith(DELAY)) {
                String raw = trimmed.substring(DELAY.length()).trim();
                try {
                    long add = Long.parseLong(raw);
                    if (add < 0L) {
                        Message.warn("Events: negative [delay] value '" + raw + "'; skipping");
                        continue;
                    }
                    delayAccum.addAndGet(add);
                } catch (NumberFormatException e) {
                    Message.warn("Events: invalid [delay] value '" + raw + "'; skipping");
                }
                continue;
            }

            final boolean asConsole = trimmed.startsWith(CONSOLE);
            final String cmd = asConsole ? trimmed.substring(CONSOLE.length()).trim() : trimmed;
            if (cmd.isBlank()) {
                continue;
            }

            Tensa.server.getScheduler().buildTask(Tensa.pluginContainer, () -> {
                        if (!event.enabled()) {
                            return;
                        }
                        sendCommand(context, cmd, asConsole);
                    })
                    .delay(delayAccum.get(), TimeUnit.SECONDS)
                    .schedule();
        }
    }

    private static EventContextBuilder context(String eventName) {
        return new EventContextBuilder().put("event", eventName);
    }

    private static String getCurrentServerName(Player player) {
        return player.getCurrentServer().map(serverConnection -> serverConnection.getServerInfo().getName()).orElse("");
    }

    private static EventContextBuilder withConnection(EventContextBuilder builder, InboundConnection connection) {
        if (connection == null) {
            return builder;
        }

        InetSocketAddress remoteAddress = connection.getRemoteAddress();
        if (remoteAddress != null) {
            builder
                    .put("ip", safe(remoteAddress.getHostString()))
                    .put("port", Integer.toString(remoteAddress.getPort()))
                    .put("address", safe(remoteAddress.getHostString()) + ":" + remoteAddress.getPort());
        }

        connection.getVirtualHost().ifPresent(virtualHost -> builder.put("host", safe(virtualHost.getHostString())));
        if (builder.isBlank("host")) {
            connection.getRawVirtualHost().ifPresent(rawHost -> builder.put("host", safe(rawHost)));
        }

        builder
                .put("protocol", safe(String.valueOf(connection.getProtocolVersion())))
                .put("intent", safe(String.valueOf(connection.getHandshakeIntent())));

        return builder;
    }

    private static EventContextBuilder withPlayer(EventContextBuilder builder, Player player) {
        if (player == null) {
            return builder;
        }

        builder.player(player)
                .put("player", safe(player.getUsername()))
                .put("uuid", safe(String.valueOf(player.getUniqueId())))
                .put("server", getCurrentServerName(player));

        return withConnection(builder, player);
    }

    private static EventContextBuilder withServerNames(EventContextBuilder builder, String currentServer, String previousServer) {
        return builder
                .put("server", currentServer)
                .put("toServer", currentServer)
                .put("fromServer", previousServer);
    }

    private static String serverName(RegisteredServer server) {
        return server == null ? EMPTY : safe(server.getServerInfo().getName());
    }

    public static void onPreLogin(PreLoginEvent event) {
        EventContext context = withConnection(context(on_pre_login_commands.name()), event.getConnection())
                .put("player", safe(event.getUsername()))
                .put("uuid", event.getUniqueId() == null ? EMPTY : event.getUniqueId().toString())
                .build();
        execute(on_pre_login_commands, context);
    }

    public static void onLogin(LoginEvent event) {
        EventContext context = withPlayer(context(on_login_commands.name()), event.getPlayer()).build();
        execute(on_login_commands, context);
    }

    public static void onPlayerJoin(PostLoginEvent event) {
        EventContextBuilder builder = withPlayer(context(on_join_commands.name()), event.getPlayer());
        execute(on_join_commands, builder.build());

        if (Tensa.userData == null) {
            return;
        }

        Tensa.userData.recordLoginAsync(UserDataService.fromPlayer(event.getPlayer()))
                .thenAccept(result -> Tensa.server.getScheduler()
                        .buildTask(Tensa.pluginContainer, () -> {
                            if (Tensa.userMeta != null) {
                                Tensa.userMeta.preloadAsync(event.getPlayer().getUniqueId());
                            }
                            if (!result.firstJoin()) {
                                return;
                            }
                            EventContext firstJoinContext = withPlayer(context(on_first_join_commands.name()), event.getPlayer())
                                    .put("firstJoinAt", Instant.ofEpochMilli(result.profile().firstSeenAt()).toString())
                                    .build();
                            execute(on_first_join_commands, firstJoinContext);
                        })
                        .schedule())
                .exceptionally(ex -> {
                    Message.error("Events: failed to record user login for " + event.getPlayer().getUsername() + ": " + ex.getMessage());
                    return null;
                });
    }

    public static void onPlayerLeave(DisconnectEvent event) {
        EventContext context = withPlayer(context(on_leave_commands.name()), event.getPlayer()).build();
        if (Tensa.userData != null) {
            Tensa.userData.recordDisconnectAsync(
                    event.getPlayer().getUniqueId(),
                    System.currentTimeMillis(),
                    getCurrentServerName(event.getPlayer())
            ).exceptionally(ex -> {
                Message.error("Events: failed to record user disconnect for " + event.getPlayer().getUsername() + ": " + ex.getMessage());
                return null;
            });
        }
        execute(on_leave_commands, context);
    }

    public static void onPlayerKick(KickedFromServerEvent event) {
        EventContext context = withServerNames(
                withPlayer(context(on_server_kick.name()), event.getPlayer()),
                serverName(event.getServer()),
                getCurrentServerName(event.getPlayer())
        ).build();
        execute(on_server_kick, context);
    }

    public static void onChooseInitialServer(PlayerChooseInitialServerEvent event) {
        String initialServer = event.getInitialServer().map(EventManager::serverName).orElse(EMPTY);
        EventContext context = withPlayer(context(on_initial_server_commands.name()), event.getPlayer())
                .put("server", initialServer)
                .put("toServer", initialServer)
                .put("initialServer", initialServer)
                .build();
        execute(on_initial_server_commands, context);
    }

    public static void onServerPreConnect(ServerPreConnectEvent event) {
        String previousServer = serverName(event.getPreviousServer());
        String originalServer = serverName(event.getOriginalServer());
        String targetServer = event.getResult().getServer().map(EventManager::serverName).orElse(originalServer);

        EventContext context = withPlayer(context(on_server_pre_connect.name()), event.getPlayer())
                .put("server", targetServer)
                .put("toServer", targetServer)
                .put("fromServer", previousServer)
                .put("originalServer", originalServer)
                .build();
        execute(on_server_pre_connect, context);
    }

    public static void onServerSwitch(ServerConnectedEvent event) {
        if (event.getPreviousServer().isEmpty()) {
            return;
        }
        String currentServerName = event.getServer().getServerInfo().getName();
        String preServer = event.getPreviousServer().map(serverConnection -> serverConnection.getServerInfo().getName())
                .orElse("");
        EventContext context = withServerNames(
                withPlayer(context(on_server_switch.name()), event.getPlayer()),
                currentServerName,
                preServer
        ).build();
        execute(on_server_switch, context);
    }

    public static void onServerPostConnect(ServerPostConnectEvent event) {
        String currentServer = getCurrentServerName(event.getPlayer());
        String previousServer = serverName(event.getPreviousServer());
        EventContext context = withServerNames(
                withPlayer(context(on_server_post_connect.name()), event.getPlayer()),
                currentServer,
                previousServer
        ).build();
        execute(on_server_post_connect, context);
    }

    public static void onListenerBound(com.velocitypowered.api.event.proxy.ListenerBoundEvent event) {
        EventContext context = context(on_listener_bound.name())
                .listener(event.getAddress(), event.getListenerType())
                .build();
        execute(on_listener_bound, context);
    }

    public static void onListenerClose(com.velocitypowered.api.event.proxy.ListenerCloseEvent event) {
        EventContext context = context(on_listener_close.name())
                .listener(event.getAddress(), event.getListenerType())
                .build();
        execute(on_listener_close, context);
    }

    public static void onServerRunning() {
        execute(on_server_running, context(on_server_running.name()).build());
    }

    public static void onProxyReload() {
        execute(on_proxy_reload, context(on_proxy_reload.name()).build());
    }

    public static void onServerPreStop() {
        execute(on_server_pre_stop, context(on_server_pre_stop.name()).build());
    }

    public static void onServerStop() {
        execute(on_server_stop, context(on_server_stop.name()).build());
    }

    private static String safe(String value) {
        return value == null ? EMPTY : value;
    }

    private record EventContext(Player player, Map<String, String> placeholders) {
    }

    private static final class EventContextBuilder {
        private Player player;
        private final Map<String, String> placeholders = new LinkedHashMap<>();

        private EventContextBuilder() {
            for (String placeholder : SUPPORTED_PLACEHOLDERS) {
                placeholders.put(placeholder, EMPTY);
            }
        }

        private EventContextBuilder player(Player player) {
            this.player = player;
            return this;
        }

        private EventContextBuilder put(String key, String value) {
            if (key == null || key.isBlank()) {
                return this;
            }
            placeholders.put(key, safe(value));
            return this;
        }

        private boolean isBlank(String key) {
            String value = placeholders.get(key);
            return value == null || value.isBlank();
        }

        private EventContextBuilder listener(InetSocketAddress address, ListenerType listenerType) {
            put("listener", listenerType == null ? EMPTY : listenerType.name());
            if (address != null) {
                put("host", address.getHostString());
                put("port", Integer.toString(address.getPort()));
                put("address", address.getHostString() + ":" + address.getPort());
            }
            return this;
        }

        private EventContext build() {
            return new EventContext(player, Collections.unmodifiableMap(new LinkedHashMap<>(placeholders)));
        }
    }
}
