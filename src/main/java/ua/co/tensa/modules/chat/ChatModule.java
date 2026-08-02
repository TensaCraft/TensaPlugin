package ua.co.tensa.modules.chat;

import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import ua.co.tensa.modules.AbstractModule;
import ua.co.tensa.modules.ModuleEntry;
import ua.co.tensa.modules.chat.data.ChatConfig;

import com.velocitypowered.api.proxy.Player;

import java.util.function.Consumer;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Chat module without event listeners and without alias handling.
 * Uses only explicit Velocity commands (registered by ChatCommands).
 */
public class ChatModule {

    private static volatile ProxyChatService proxyChat;
    private static final CopyOnWriteArrayList<Consumer<ProxyChatMessage>> OUTBOUND_SINKS =
            new CopyOnWriteArrayList<>();

    private static final ModuleEntry IMPL = new AbstractModule(
            "chat-manager", "Chat Manager") {
        @Override protected void onEnable() {
            ChatConfig.get().reloadCfg();
            proxyChat = new ProxyChatService();
            registerListener(new ProxyChatListener(proxyChat));
            ChatCommands.register();
        }
        @Override protected void onDisable() {
            ChatCommands.unregister();
            ProxyChatService service = proxyChat;
            proxyChat = null;
            if (service != null) {
                service.clear();
            }
        }
        @Override protected void onReload() {
            ChatConfig.get().reloadCfg();
            ProxyChatService service = proxyChat;
            if (service == null) {
                service = new ProxyChatService();
                proxyChat = service;
                registerListener(new ProxyChatListener(service));
            } else {
                service.reload();
            }
            ChatCommands.unregister();
            ChatCommands.register();
        }
    };

    public static final ModuleEntry ENTRY = IMPL;

    public static void enable() { IMPL.enable(); }
    public static void disable() { IMPL.disable(); }

    public static boolean publishPlayerMessage(Player player, String message) {
        ProxyChatService service = proxyChat;
        return service != null && service.publishPlayer(player, message, "global");
    }

    /** Publish an external message without notifying outbound sinks, preventing relay loops. */
    public static void publishExternalMessage(String source, String author, String message) {
        ProxyChatService service = proxyChat;
        if (service != null) {
            service.publishExternal(source, author, message);
        }
    }

    /** Subscribe to sanitized Minecraft chat messages. Closing the handle unregisters the sink. */
    public static AutoCloseable registerOutboundSink(Consumer<ProxyChatMessage> sink) {
        if (sink == null) {
            return () -> { };
        }
        OUTBOUND_SINKS.add(sink);
        return () -> OUTBOUND_SINKS.remove(sink);
    }

    static void notifyOutbound(ProxyChatMessage payload) {
        for (Consumer<ProxyChatMessage> sink : OUTBOUND_SINKS) {
            try {
                sink.accept(payload);
            } catch (RuntimeException exception) {
                Message.warn("Chat relay rejected a message: " + exception.getMessage());
            }
        }
    }

    /** Fanout helper: send a message to everyone or only to players with a permission. */
    public static void sendMessageToPermittedPlayers(String message, String permission) {
        if (permission == null || permission.isBlank()) {
            Tensa.server.getAllPlayers().forEach(p -> Message.send(p, message));
            Message.send(Tensa.server.getConsoleCommandSource(), message);
            return;
        }
        Tensa.server.getAllPlayers().stream()
                .filter(p -> p.hasPermission(permission))
                .forEach(p -> Message.send(p, message));
        Message.send(Tensa.server.getConsoleCommandSource(), message);
    }
}
