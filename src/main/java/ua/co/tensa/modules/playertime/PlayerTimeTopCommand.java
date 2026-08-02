package ua.co.tensa.modules.playertime;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import ua.co.tensa.Util;
import ua.co.tensa.config.Lang;

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

        this.timeTracker.getTopPlayers(limit).thenAccept(entries -> {
            if (entries == null || entries.isEmpty()) {
                Message.sendLang(sender, Lang.player_time_top);
                return;
            }
            Message.sendLang(sender, Lang.player_time_top);
            int position = 1;
            for (PlayerTimeTracker.PlayerTimeEntry entry : entries) {
                String playTime = PlayerTimeModule.formatTime(entry.playTime());
                Message.sendLang(sender, Lang.player_time_top_entry,
                        "{position}", String.valueOf(position),
                        "{player}", Message.escapeMiniMessage(entry.playerName()),
                        "{time}", playTime);
                position += 1;
            }
        });


    }

    public static void unregister() {
        Util.unregisterCommand("tplayertop");
        Util.unregisterCommand("tptop");
    }
}
