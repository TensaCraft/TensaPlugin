package ua.co.tensa.modules.playertime;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import ua.co.tensa.config.Lang;

import java.sql.SQLException;

public class PlayerTimeTopCommand implements SimpleCommand {

    private final PlayerTimeTracker timeTracker;
    public PlayerTimeTopCommand(PlayerTimeTracker timeTracker) {
        this.timeTracker = timeTracker;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource sender = invocation.source();

        if (!sender.hasPermission("tensa.playertime.top") && !sender.hasPermission("tensa.playertime.admin")) {
            Message.sendLang(sender, Lang.no_perms);
            return;
        }
        int limit = 10;

        this.timeTracker.getTopPlayers(limit).thenAccept(result -> {
            try {
                Message.sendLang(sender, Lang.player_time_top);
                int position = 1;
                while (result.next()) {
                    String playerInfo = result.getString(1);
                    String playTime = PlayerTimeModule.formatTime(Long.parseLong(result.getString(2)));
                    Message.sendLang(sender, Lang.player_time_top_entry,
                            "{position}", String.valueOf(position),
                            "{player}", playerInfo,
                            "{time}", playTime);
                    position += 1;
                }
            } catch (SQLException e) {
                ua.co.tensa.Message.error(e.getMessage());
            } finally {
                try {
                    result.close();
                } catch (SQLException e) {
                    ua.co.tensa.Message.error(e.getMessage());
                }
            }
        });


    }

    public static void unregister() {
        CommandManager manager = Tensa.server.getCommandManager();
        manager.unregister("tplayertop");
        manager.unregister("tptop");
    }
}
