package ua.co.tensa.modules.chat;

import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import ua.co.tensa.modules.AbstractModule;
import ua.co.tensa.modules.ModuleEntry;
import ua.co.tensa.modules.chat.data.ChatConfig;

/**
 * Chat module without event listeners and without alias handling.
 * Uses only explicit Velocity commands (registered by ChatCommands).
 */
public class ChatModule {

    private static final ModuleEntry IMPL = new AbstractModule(
            "chat-manager", "Chat Manager") {
        @Override protected void onEnable() {
            ChatConfig.get().reloadCfg();
            ChatCommands.register();
        }
        @Override protected void onDisable() { ChatCommands.unregister(); }
        @Override protected void onReload() {
            ChatConfig.get().reloadCfg();
            ChatCommands.unregister();
            ChatCommands.register();
        }
    };

    public static final ModuleEntry ENTRY = IMPL;

    public static void enable() { IMPL.enable(); }
    public static void disable() { IMPL.disable(); }

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
