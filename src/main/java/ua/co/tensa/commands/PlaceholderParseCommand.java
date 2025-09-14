package ua.co.tensa.commands;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PlaceholderParseCommand implements SimpleCommand {

    @Override
    public void execute(Invocation invocation) {
        CommandSource sender = invocation.source();
        String[] args = invocation.arguments();

        if (!sender.hasPermission("tensa.placeholder.parse")) {
            Message.send(sender, "You don't have permission.");
            return;
        }

        if (args.length == 0) {
            Message.send(sender, "Usage: /tparse <text with placeholders>");
            return;
        }

        String input = String.join(" ", args);
        Player player = sender instanceof Player ? (Player) sender : null;
        Message.send(sender, input);
    }

    public static void unregister() {
        CommandManager manager = Tensa.server.getCommandManager();
        manager.unregister("tparse");
        manager.unregister("tph");
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        return CompletableFuture.completedFuture(Arrays.asList());
    }
}
