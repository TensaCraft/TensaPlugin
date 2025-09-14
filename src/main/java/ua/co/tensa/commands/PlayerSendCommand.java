package ua.co.tensa.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import ua.co.tensa.config.Lang;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class PlayerSendCommand implements SimpleCommand {
    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!hasPermission(invocation)) {
            Message.sendLang(source, Lang.no_perms);
            return;
        }
        String[] args = invocation.arguments();
        if (args.length < 2) {
            Message.sendLang(source, Lang.send_usage);
            return;
        }

        Optional<RegisteredServer> toServer = Tensa.server.getServer(args[1]);
        if (toServer.isEmpty()) {
            Message.sendLang(source, Lang.server_not_found, "{server}", args[1]);
            return;
        }

        if (args[0].equals("all")) {
            for (Player p : Tensa.server.getAllPlayers()) {
                p.createConnectionRequest(toServer.get()).fireAndForget();
            }
            Message.sendLang(source, Lang.send_success, "{player}", "all", "{server}", args[1]);
            return;
        }

        Optional<Player> player = Tensa.server.getPlayer(args[0]);
        if (player.isEmpty()) {
            Message.sendLang(source, Lang.player_not_found, "{player}", args[0]);
            return;
        }

        player.get().createConnectionRequest(toServer.get()).fireAndForget();
        Message.sendLang(source, Lang.send_success, "{player}", args[0], "{server}", args[1]);
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        ArrayList<String> args = new ArrayList<>();
        int argNum = invocation.arguments().length;
        if (argNum == 0 || argNum == 1) {
            args.add("all");
            for (Player player : Tensa.server.getAllPlayers()) {
                args.add(player.getUsername().trim());
            }
        }
        if (argNum == 2) {
            Tensa.server.getAllServers().forEach(serverConnection -> args.add(serverConnection.getServerInfo().getName()));
        }
        return CompletableFuture.completedFuture(args);
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("tensa.send.player");
    }
}
