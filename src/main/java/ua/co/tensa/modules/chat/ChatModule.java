package ua.co.tensa.modules.chat;

import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import net.kyori.adventure.text.Component;

public class ChatModule {

    public static void initialise() {
         ChatEventListener.reload();
         ChatCommands.reload();
    }

    public static void reload() {
        initialise();
    }

    public static void enable() {
        initialise();
        Message.info("Chat Manager module enabled");
    }

    public static void disable() {
        initialise();
    }

    public static void sendMessageToPermittedPlayers(String message, String permission) {
        final Component messageFormat = Message.convert(message);
        if (permission == null || permission.isBlank()) {
            Tensa.server.sendMessage(messageFormat);
        } else {
            Tensa.server.getAllPlayers().stream()
                    .filter(p -> p.hasPermission(permission))
                    .forEach(p -> p.sendMessage(messageFormat));
            Tensa.server.getConsoleCommandSource().sendMessage(messageFormat);
        }
    }
}
