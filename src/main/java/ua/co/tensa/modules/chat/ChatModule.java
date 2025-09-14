package ua.co.tensa.modules.chat;

import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import ua.co.tensa.modules.AbstractModule;
import ua.co.tensa.modules.ModuleEntry;

public class ChatModule {

    private static final ModuleEntry IMPL = new AbstractModule(
            "chat-manager", "Chat Manager") {
        @Override protected void onEnable() { ChatModule.enableImpl(); }
        @Override protected void onDisable() { ChatModule.disableImpl(); }
    };
    public static final ModuleEntry ENTRY = IMPL;

    public static void initialise() {
         ChatEventListener.reload();
         ChatCommands.reload();
    }

    public static void reload() {
        initialise();
    }

    private static void enableImpl() { initialise(); ua.co.tensa.Message.info("Chat Manager module enabled"); }
    private static void disableImpl() { initialise(); }

    public static void enable() { IMPL.enable(); }
    public static void disable() { IMPL.disable(); }

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
