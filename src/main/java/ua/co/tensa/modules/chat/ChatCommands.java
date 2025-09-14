package ua.co.tensa.modules.chat;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import ua.co.tensa.config.core.ConfigAdapter;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import ua.co.tensa.Util;
import ua.co.tensa.config.Config;
import ua.co.tensa.config.Lang;
import ua.co.tensa.config.data.ChatYAML;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class ChatCommands implements SimpleCommand {

    private static final boolean chatEnabled = Config.getModules("chat-manager");
    private static ConfigAdapter chatCfg = ChatYAML.getInstance().adapter();

    public static void reload() {
        ChatYAML.getInstance().reload();
        chatCfg = ChatYAML.getInstance().adapter();
        unregister();
        register();
    }

    public static void register() {
        if (!chatEnabled) {
            return;
        }
        // If the module is enabled, register commands
        chatCfg.getKeys(false)
                .stream()
                .filter(key -> chatCfg.getBoolean(key + ".enabled", false))
                .forEach(key -> {
                    String command = chatCfg.getString(key + ".command", "");
                    Util.registerCommand(command, "", new ChatCommands());
                });
    }

    public static void unregister() {
        if (!chatEnabled) {
            // If the module is excluded, disable all commands
            chatCfg.getKeys(false)
                    .forEach(key -> Util.unregisterCommand(chatCfg.getString(key + ".command", "")));
        } else {
            chatCfg.getKeys(false)
                    .stream()
                    .filter(key -> !chatCfg.getBoolean(key + ".enabled", false))
                    .forEach(key -> Util.unregisterCommand(chatCfg.getString(key + ".command", "")));
        }
    }

    @Override
    public void execute(Invocation invocation) {
        if (!chatEnabled) {
            return;
        }

        String server;
        String playerName;
        if (invocation.source() instanceof Player player) {
            server = player.getCurrentServer()
                    .map(s -> s.getServerInfo().getName())
                    .orElse("");
            playerName = player.getUsername();
        } else {
            playerName = "";
            server = "";
        }

        String command = invocation.alias();
        Set<String> chats = chatCfg.getKeys(false);
        chats.forEach(key -> {
            if (chatCfg.getBoolean(key + ".enabled", false) && command.equals(chatCfg.getString(key + ".command", ""))) {
                String type = chatCfg.getString(key + ".type", "public");
                if ("private".equals(type)) {
                    handlePrivateChat(invocation, key, server, playerName);
                } else {
                    handlePublicChat(invocation, key, server, playerName);
                }
            }
        });
    }

    private void handlePrivateChat(Invocation invocation, String key, String server, String playerName) {
        CommandSource sender = invocation.source();
        boolean console = !(sender instanceof Player);
        if (invocation.arguments().length < 2) {
            Message.sendLang(sender, Lang.chat_usage, "{command}", invocation.alias());
            return;
        }

        String targetPlayerName = invocation.arguments()[0];
        Optional<Player> playerOptional = Tensa.server.getPlayer(targetPlayerName);
        if (playerOptional.isEmpty()) {
            Message.sendLang(sender, Lang.player_not_found, "{player}", targetPlayerName);
            return;
        }
        Player targetPlayer = playerOptional.get();

        String[] args = invocation.arguments();
        String message = args.length <= 1 ? "" : String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        if (console) {
            Message.send(targetPlayer, message);
            return;
        }
        java.util.Map<String, String> ctx = new java.util.HashMap<>();
        ctx.put("server", server);
        ctx.put("from", playerName);
        ctx.put("target", targetPlayer.getUsername());
        ctx.put("message", message);
        String toMessageFormat = Message.renderTemplateString(chatCfg.getString(key + ".to_format", "{from}: {message}"), ctx);
        Message.privateMessage(targetPlayer, toMessageFormat);

        String fromMessageFormat = Message.renderTemplateString(chatCfg.getString(key + ".from_format", "{to}: {message}"), ctx);
        Message.privateMessage(sender, fromMessageFormat);
    }

    private void handlePublicChat(Invocation invocation, String key, String server, String playerName) {
        String permission = chatCfg.getString(key + ".permission", "");
        if (!permission.isEmpty() && !invocation.source().hasPermission(permission)) {
            Message.sendLang(invocation.source(), Lang.no_perms);
            return;
        }
        String message = String.join(" ", invocation.arguments());
        java.util.Map<String, String> ctx = new java.util.HashMap<>();
        ctx.put("server", server);
        ctx.put("player", playerName);
        ctx.put("message", message);
        String messageFormatReplaced = Message.renderTemplateString(chatCfg.getString(key + ".format", "{player}: {message}"), ctx);
        if (chatCfg.getBoolean(key + ".see_all", false)) {
            ChatModule.sendMessageToPermittedPlayers(messageFormatReplaced, "");
        } else {
            ChatModule.sendMessageToPermittedPlayers(messageFormatReplaced, permission);
        }
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(final Invocation invocation) {
        String command = invocation.alias();
        // Check whether the team belongs to a private chat
        boolean isPrivate = chatCfg.getKeys(false)
                .stream()
                .anyMatch(key -> chatCfg.getBoolean(key + ".enabled", false) &&
                        command.equals(chatCfg.getString(key + ".command", "")) &&
                        "private".equals(chatCfg.getString(key + ".type", "public")));
        if (!isPrivate) {
            return CompletableFuture.completedFuture(List.of());
        }

        // We get a list of players. We assume that the getAllPlayers() method returns the list of online players.
        List<String> playerNames = Tensa.server.getAllPlayers()
                .stream()
                .map(Player::getUsername)
                .toList();

        // If the player has not yet started entering the name or introduced only part, return the appropriate tips
        if (invocation.arguments().length == 0) {
            return CompletableFuture.completedFuture(playerNames);
        } else if (invocation.arguments().length == 1) {
            String typed = invocation.arguments()[0].toLowerCase();
            List<String> suggestions = playerNames.stream()
                    .filter(name -> name.toLowerCase().startsWith(typed))
                    .toList();
            return CompletableFuture.completedFuture(suggestions);
        }
        // For other arguments do not return the prompts
        return CompletableFuture.completedFuture(List.of());
    }
}
