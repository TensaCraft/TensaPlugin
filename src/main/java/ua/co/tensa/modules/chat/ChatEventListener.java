package ua.co.tensa.modules.chat;

import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import ua.co.tensa.config.Config;
import ua.co.tensa.config.data.ChatYAML;

public class ChatEventListener {
    private static boolean chatEnabled = Config.getModules("chat-manager");
    private static ua.co.tensa.config.core.ConfigAdapter chatCfg = ChatYAML.getInstance().adapter();

    public static void reload() {
        chatEnabled = Config.getModules("chat-manager");
        if (chatEnabled) {
            ChatYAML.getInstance().reload();
            chatCfg = ChatYAML.getInstance().adapter();
        }
    }

    // Called from Tensa.onPlayerMessage; annotation not needed here
    public static void onPlayerMessage(PlayerChatEvent event) {
        if (!chatEnabled) return;

        final Player player = event.getPlayer();
        final String incoming = event.getMessage();
        if (incoming == null || incoming.isEmpty()) return;

        for (String key : chatCfg.getKeys(false)) {
            if (!chatCfg.getBoolean(key + ".enabled", false)) continue;

            final String permission = chatCfg.getString(key + ".permission", "").trim();
            if (!permission.isBlank() && !player.hasPermission(permission)) continue;

            final String alias = chatCfg.getString(key + ".alias", "").trim();
            if (alias.isBlank()) continue;

            if (!incoming.startsWith(alias)) continue;

            event.setResult(PlayerChatEvent.ChatResult.denied());

            final String serverName = player.getCurrentServer()
                    .map(s -> s.getServerInfo().getName())
                    .orElse("");

            final String format = chatCfg.getString(key + ".format", "{server} {player}: {message}");
            final String body = incoming.substring(Math.min(alias.length(), incoming.length())).stripLeading();

            java.util.Map<String, String> ctx = new java.util.HashMap<>();
            ctx.put("server", serverName);
            ctx.put("player", player.getUsername());
            ctx.put("message", body);

            final String out = ua.co.tensa.Message.renderTemplateString(format, ctx);

            if (chatCfg.getBoolean(key + ".see_all", false)) {
                ChatModule.sendMessageToPermittedPlayers(out, "");
            } else {
                ChatModule.sendMessageToPermittedPlayers(out, permission);
            }
            return;
        }
    }
}
