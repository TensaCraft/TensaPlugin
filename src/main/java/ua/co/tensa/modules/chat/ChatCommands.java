package ua.co.tensa.modules.chat;

import com.velocitypowered.api.command.CommandSource;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import ua.co.tensa.Util;
import ua.co.tensa.config.Config;
import ua.co.tensa.config.Lang;
import ua.co.tensa.config.data.ChatYAML;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import org.simpleyaml.configuration.file.YamlConfiguration;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class ChatCommands implements SimpleCommand {

    private static final boolean chatEnabled = Config.getModules("chat-manager");
    private static YamlConfiguration chatConfig = ChatYAML.getInstance().getConfig();

    public static void reload() {
        chatConfig = ChatYAML.getInstance().getReloadedFile();
        unregister();
        register();
    }

    public static void register() {
        if (!chatEnabled) {
            return;
        }
        // If the module is enabled, register commands
        chatConfig.getKeys(false)
                .stream()
                .filter(key -> chatConfig.getBoolean(key + ".enabled"))
                .forEach(key -> {
                    String command = chatConfig.getString(key + ".command");
                    Util.registerCommand(command, "", new ChatCommands());
                });
    }

    public static void unregister() {
        if (!chatEnabled) {
            // If the module is excluded, disable all commands
            chatConfig.getKeys(false)
                    .forEach(key -> Util.unregisterCommand(chatConfig.getString(key + ".command")));
        } else {
            chatConfig.getKeys(false)
                    .stream()
                    .filter(key -> !chatConfig.getBoolean(key + ".enabled"))
                    .forEach(key -> Util.unregisterCommand(chatConfig.getString(key + ".command")));
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
        Set<String> chats = chatConfig.getKeys(false);
        chats.forEach(key -> {
            if (chatConfig.getBoolean(key + ".enabled") && command.equals(chatConfig.getString(key + ".command"))) {
                String type = chatConfig.getString(key + ".type");
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
            sender.sendMessage(Lang.chat_usage.replace("{command}", invocation.alias()));
            return;
        }

        String targetPlayerName = invocation.arguments()[0];
        Optional<Player> playerOptional = Tensa.server.getPlayer(targetPlayerName);
        if (playerOptional.isEmpty()) {
            sender.sendMessage(Lang.player_not_found.replace("{player}", targetPlayerName));
            return;
        }
        Player targetPlayer = playerOptional.get();

        String message = Arrays.stream(invocation.arguments())
                .skip(1)
                .map(arg -> arg + " ")
                .reduce("", String::concat);

        if (console) {
            targetPlayer.sendMessage(Message.convert(message));
            return;
        }
        String toMessageFormat = chatConfig.getString(key + ".to_format")
                .replace("{server}", server)
                .replace("{from}", playerName)
                .replace("{target}", targetPlayer.getUsername())
                .replace("{message}", message);
        Message.privateMessage(targetPlayer, toMessageFormat);

        String fromMessageFormat = chatConfig.getString(key + ".from_format")
                .replace("{server}", server)
                .replace("{from}", playerName)
                .replace("{target}", targetPlayer.getUsername())
                .replace("{message}", message);
        Message.privateMessage(sender, fromMessageFormat);
    }

    private void handlePublicChat(Invocation invocation, String key, String server, String playerName) {
        String permission = chatConfig.getString(key + ".permission");
        if (!permission.isEmpty() && !invocation.source().hasPermission(permission)) {
            invocation.source().sendMessage(Lang.no_perms.get());
            return;
        }
        String message = Arrays.stream(invocation.arguments())
                .map(arg -> arg + " ")
                .reduce("", String::concat);
        String messageFormat = chatConfig.getString(key + ".format");
        String messageFormatReplaced = messageFormat
                .replace("{server}", server)
                .replace("{player}", playerName)
                .replace("{message}", message);
        if (chatConfig.getBoolean(key + ".see_all")) {
            ChatModule.sendMessageToPermittedPlayers(messageFormatReplaced, "");
        } else {
            ChatModule.sendMessageToPermittedPlayers(messageFormatReplaced, permission);
        }
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(final Invocation invocation) {
        String command = invocation.alias();
        // Check whether the team belongs to a private chat
        boolean isPrivate = chatConfig.getKeys(false)
                .stream()
                .anyMatch(key -> chatConfig.getBoolean(key + ".enabled") &&
                        command.equals(chatConfig.getString(key + ".command")) &&
                        "private".equals(chatConfig.getString(key + ".type")));
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
