package ua.co.tensa.modules.playertime;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import ua.co.tensa.config.Lang;
import ua.co.tensa.modules.AbstractModule;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;


public class PlayerTimeCommand implements SimpleCommand {

    private final PlayerTimeTracker timeTracker;

    public PlayerTimeCommand(PlayerTimeTracker timeTracker) {
        this.timeTracker = timeTracker;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource sender = invocation.source();
        String[] args = invocation.arguments();

        if (!sender.hasPermission("tensa.playertime")) {
            Message.sendLang(sender, Lang.no_perms);
            return;
        }

        if (args.length == 0 && sender instanceof Player) {
            Player player = (Player) sender;
            try {
                timeTracker.getCurrentPlayerTime(player.getUniqueId()).thenAccept(result -> {
                    if (result == null) {
                        ua.co.tensa.Message.warn("PlayerTime: DB returned no result for current player time");
                        return;
                    }
                    try {
                        if (result.next()) {
                            Message.sendLang(sender, Lang.player_time,
                                    "{time}", PlayerTimeModule.formatTime(Long.parseLong(result.getString(1))));
                        }
                    } catch (SQLException e) {
                        ua.co.tensa.Message.error(e.getMessage());
                    } finally {
                        try { result.close(); } catch (SQLException ignored) {}
                    }
                });
            } catch (SQLException e) {
                ua.co.tensa.Message.error(e.getMessage());
            }
        } else if (args.length == 1 && sender.hasPermission("TENSA.playertime.admin")) {
            String playerName = args[0];
            timeTracker.getPlayerTimeByName(playerName).thenAccept(result -> {
                if (result == null) {
                    ua.co.tensa.Message.warn("PlayerTime: DB returned no result for name query: " + playerName);
                    return;
                }
                try {
                    if (result.next()) {
                        Message.sendLang(sender, Lang.player_time_other,
                                "{player}", playerName,
                                "{time}", PlayerTimeModule.formatTime(Long.parseLong(result.getString(1))));
                    } else {
                        Message.sendLang(sender, Lang.player_not_found, "{player}", playerName);
                    }
                } catch (SQLException e) {
                    ua.co.tensa.Message.error(e.getMessage());
                } finally {
                    try { result.close(); } catch (SQLException ignored) {}
                }
            });
        } else {
            Message.sendLang(sender, Lang.player_time_usage);
        }
    }

    public static void unregister() {
        CommandManager manager = Tensa.server.getCommandManager();
        manager.unregister("tplayertime");
        manager.unregister("tptime");
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(final Invocation invocation) {
        ArrayList<String> suggestions = new ArrayList<>();
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        if (args.length == 0 && hasAdminPermission(source)) {
            for (Player player : Tensa.server.getAllPlayers()) {
                suggestions.add(player.getUsername().trim());
            }
        }
        return CompletableFuture.completedFuture(suggestions);
    }

    private boolean hasAdminPermission(CommandSource source) {
        return source.hasPermission("tensa.playertime.admin");
    }
}
