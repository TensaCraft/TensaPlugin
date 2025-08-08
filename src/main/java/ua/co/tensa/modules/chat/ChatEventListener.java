package ua.co.tensa.modules.chat;

import ua.co.tensa.config.Config;
import ua.co.tensa.config.data.ChatYAML;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import org.simpleyaml.configuration.file.YamlConfiguration;
import java.util.Set;

public class ChatEventListener {
    private static boolean chatEnabled = Config.getModules("chat-manager");
    private static YamlConfiguration chatConfig = ChatYAML.getInstance().getConfig();


    public static void reload() {
        chatEnabled = Config.getModules("chat-manager");
        if (chatEnabled) {
            chatConfig = ChatYAML.getInstance().getReloadedFile();
        }
    }

    @Subscribe
    public static void onPlayerMessage(PlayerChatEvent event) {
        if (!chatEnabled || chatConfig == null) return;

        final Player player = event.getPlayer();
        final String incoming = String.valueOf(event.getMessage());
        if (incoming == null || incoming.isEmpty()) return;

        for (String key : chatConfig.getKeys(false)) {
            if (!chatConfig.getBoolean(key + ".enabled", false)) continue;

            final String permission = chatConfig.getString(key + ".permission", "").trim();
            if (!permission.isBlank() && !player.hasPermission(permission)) continue;

            final String alias = chatConfig.getString(key + ".alias", "").trim();
            if (alias.isBlank()) continue;

            if (!incoming.startsWith(alias)) continue;

            event.setResult(PlayerChatEvent.ChatResult.denied());

            final String serverName = player.getCurrentServer()
                    .map(s -> s.getServerInfo().getName())
                    .orElse("");

            final String format = chatConfig.getString(key + ".format", "{server} {player}: {message}");
            final String body = incoming.substring(alias.length()).stripLeading();

            final String out = format
                    .replace("{server}", serverName)
                    .replace("{player}", player.getUsername())
                    .replace("{message}", body);

            if (chatConfig.getBoolean(key + ".see_all", false)) {
                ChatModule.sendMessageToPermittedPlayers(out, "");
            } else {
                ChatModule.sendMessageToPermittedPlayers(out, permission);
            }
            return;
        }
    }
}
