package ua.co.tensa.modules.chat;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.minimessage.MiniMessage;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import ua.co.tensa.Util;
import ua.co.tensa.config.Lang;
import ua.co.tensa.config.model.YamlAdapter;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ChatCommands implements SimpleCommand {

    public record ChatRoute(String key, boolean privateRoute, boolean replyRoute) {
    }

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private final Map<UUID, UUID> lastPrivateTarget = new ConcurrentHashMap<>();
    private final YamlAdapter chatCfg;
    private final ProxyChatService proxyChat;

    public ChatCommands(YamlAdapter chatCfg, ProxyChatService proxyChat) {
        this.chatCfg = Objects.requireNonNull(chatCfg, "chatCfg");
        this.proxyChat = Objects.requireNonNull(proxyChat, "proxyChat");
    }

    private static boolean isConsole(CommandSource source) {
        return source instanceof ConsoleCommandSource;
    }

    private static String normalizeCmd(String cmd) {
        if (cmd == null) return null;
        String value = cmd.trim();
        if (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value.trim();
    }

    private static String secString(Map<String, Object> sec, String key, String def) {
        if (sec == null) return def;

        Object value = sec.get(key);
        if (value == null) return def;
        if (value instanceof String text) return text;

        return String.valueOf(value);
    }

    private static boolean secBool(Map<String, Object> sec, String key, boolean def) {
        if (sec == null) return def;

        Object value = sec.get(key);
        if (value == null) return def;
        if (value instanceof Boolean bool) return bool;
        if (value instanceof String text) return Boolean.parseBoolean(text.trim());

        return def;
    }

    private static List<String> secCommands(Map<String, Object> sec) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (sec == null) return List.of();

        Object commands = sec.get("commands");
        if (commands instanceof Collection<?> col) {
            for (Object item : col) {
                if (item == null) continue;

                String cmd = normalizeCmd(String.valueOf(item));
                if (cmd != null && !cmd.isBlank()) {
                    out.add(cmd);
                }
            }
        }

        String single = normalizeCmd(secString(sec, "command", ""));
        if (single != null && !single.isBlank()) {
            String[] parts = single.split("[,;\\s]+");
            for (String part : parts) {
                String cmd = normalizeCmd(part);
                if (cmd != null && !cmd.isBlank()) {
                    out.add(cmd);
                }
            }
        }

        return new ArrayList<>(out);
    }

    private Map<String, Object> findSection(String alias) {
        String used = normalizeCmd(alias);
        if (used == null || used.isBlank()) {
            return null;
        }

        for (String key : chatCfg.getKeys(false)) {
            Map<String, Object> sec = chatCfg.getSection(key);
            if (sec == null || sec.isEmpty()) continue;
            if (!secBool(sec, "enabled", true)) continue;

            if (secCommands(sec).contains(used)) {
                return sec;
            }
        }

        return null;
    }

    private static boolean isPrivate(Map<String, Object> sec) {
        return "private".equalsIgnoreCase(secString(sec, "type", "public"));
    }

    private static boolean isReply(Map<String, Object> sec) {
        return "reply".equalsIgnoreCase(secString(sec, "type", "public"));
    }

    private String findSectionKey(String alias) {
        String used = normalizeCmd(alias);
        if (used == null || used.isBlank()) {
            return "";
        }

        for (String key : chatCfg.getKeys(false)) {
            Map<String, Object> sec = chatCfg.getSection(key);
            if (sec == null || sec.isEmpty() || !secBool(sec, "enabled", true)) {
                continue;
            }
            if (secCommands(sec).contains(used)) {
                return key;
            }
        }
        return "";
    }

    private static String getServerName(CommandSource source) {
        if (source instanceof Player player) {
            return player.getCurrentServer()
                    .map(server -> server.getServerInfo().getName())
                    .orElse("");
        }

        return "";
    }

    private static String getSenderName(CommandSource source) {
        if (source instanceof Player player) {
            return player.getUsername();
        }

        if (source instanceof ConsoleCommandSource) {
            return "Console";
        }

        return "Unknown";
    }

    private static Map<String, String> publicCtx(String server, String player, String msg) {
        Map<String, String> ctx = new HashMap<>();
        ctx.put("server", Message.escapeMiniMessage(server));
        ctx.put("player", Message.escapeMiniMessage(player));
        ctx.put("message", Message.escapeMiniMessage(msg));
        ctx.put("message_payload", Message.escapeMiniMessageArgument(msg));
        return ctx;
    }

    static Map<String, String> privateCtx(String server, String from, String to, String msg) {
        Map<String, String> ctx = new HashMap<>();
        ctx.put("server", Message.escapeMiniMessage(server));
        ctx.put("from", Message.escapeMiniMessage(from));
        ctx.put("to", Message.escapeMiniMessage(to));
        ctx.put("target", Message.escapeMiniMessage(to));
        ctx.put("message", Message.escapeMiniMessage(msg));
        ctx.put("message_payload", Message.escapeMiniMessageArgument(msg));
        return ctx;
    }

    private static void sendMini(CommandSource target, String msg) {
        target.sendMessage(MINI.deserialize(msg));
    }

    private static void sendMiniToPlayers(String msg, String perm, boolean seeAll) {
        for (Player target : Tensa.server.getAllPlayers()) {
            if (seeAll || perm.isEmpty() || target.hasPermission(perm)) {
                sendMini(target, msg);
            }
        }
    }

    public void register() {
        for (String key : chatCfg.getKeys(false)) {
            Map<String, Object> sec = chatCfg.getSection(key);
            if (sec == null || sec.isEmpty()) continue;
            if (!secBool(sec, "enabled", true)) continue;

            for (String cmd : secCommands(sec)) {
                Util.registerCommand(cmd, "", this);
            }
        }
    }

    public void unregister() {
        for (String key : chatCfg.getKeys(false)) {
            Map<String, Object> sec = chatCfg.getSection(key);
            if (sec == null || sec.isEmpty()) continue;

            for (String cmd : secCommands(sec)) {
                Util.unregisterCommand(cmd);
            }
        }
    }

    public ChatRoute findRoute(String command) {
        String normalized = normalizeCmd(command);
        if (normalized == null || normalized.isBlank()) {
            return null;
        }

        for (String key : chatCfg.getKeys(false)) {
            Map<String, Object> sec = chatCfg.getSection(key);
            if (sec == null || sec.isEmpty()) continue;
            if (!secBool(sec, "enabled", true)) continue;

            if (!secCommands(sec).contains(normalized)) continue;

            return new ChatRoute(key, isPrivate(sec), isReply(sec));
        }

        return null;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        Map<String, Object> sec = findSection(invocation.alias());
        if (sec == null) return;

        String server = getServerName(source);
        String playerName = getSenderName(source);

        if (isPrivate(sec) || isReply(sec)) {
            handlePrivateChat(invocation, sec, server, playerName, isReply(sec));
            return;
        }

        handlePublicChat(invocation, sec, findSectionKey(invocation.alias()), server, playerName);
    }

    public void forgetPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        lastPrivateTarget.remove(playerId);
        lastPrivateTarget.entrySet().removeIf(entry -> playerId.equals(entry.getValue()));
    }

    private void handlePrivateChat(
            Invocation invocation,
            Map<String, Object> sec,
            String server,
            String playerName,
            boolean reply
    ) {
        CommandSource sender = invocation.source();
        boolean console = isConsole(sender);

        int messageOffset = reply ? 0 : 1;
        int requiredArguments = reply ? 1 : 2;
        if (invocation.arguments().length < requiredArguments) {
            Message.sendLang(sender, Lang.chat_usage, "{command}", Message.escapeMiniMessage(invocation.alias()));
            return;
        }

        Player target;
        String targetName;
        if (reply && sender instanceof Player player) {
            UUID targetId = lastPrivateTarget.get(player.getUniqueId());
            target = targetId == null ? null : Tensa.server.getPlayer(targetId).orElse(null);
            targetName = target == null ? "" : target.getUsername();
        } else {
            targetName = invocation.arguments()[0];
            target = Tensa.server.getPlayer(targetName).orElse(null);
        }
        if (target == null) {
            if (reply) {
                Message.privateMessage(sender, "<color:#ffb84d>Немає активної приватної розмови або гравець уже офлайн.</color>");
                return;
            }
            Message.sendLang(sender, Lang.player_not_found, "{player}", Message.escapeMiniMessage(targetName));
            return;
        }

        String rawMessage = String.join(
                " ",
                Arrays.copyOfRange(invocation.arguments(), messageOffset, invocation.arguments().length)
        );
        String msg = ProxyChatText.sanitize(rawMessage, 256, 4);
        if (msg.isBlank()) {
            return;
        }

        if (console) {
            sendMini(target, Message.escapeMiniMessage(msg));
            return;
        }

        Player player = (Player) sender;
        lastPrivateTarget.put(player.getUniqueId(), target.getUniqueId());
        lastPrivateTarget.put(target.getUniqueId(), player.getUniqueId());

        Map<String, String> ctx = privateCtx(
                server,
                playerName,
                target.getUsername(),
                msg
        );

        String toFmt = secString(sec, "to_format", "{from}: {message}");
        String fromFmt = secString(sec, "from_format", "{to}: {message}");

        String toMsg = Message.renderTemplateString(toFmt, ctx);
        Message.privateMessage(target, toMsg);

        String fromMsg = Message.renderTemplateString(fromFmt, ctx);
        Message.privateMessage(sender, fromMsg);
    }

    private void handlePublicChat(
            Invocation invocation,
            Map<String, Object> sec,
            String sectionKey,
            String server,
            String playerName
    ) {
        CommandSource source = invocation.source();
        String perm = secString(sec, "permission", "");
        boolean seeAll = secBool(sec, "see_all", false);

        if (source instanceof Player && !perm.isEmpty() && !source.hasPermission(perm)) {
            Message.sendLang(source, Lang.no_perms);
            return;
        }

        String msg = ProxyChatText.sanitize(String.join(" ", invocation.arguments()), 256, 4);
        if (msg.isBlank()) {
            return;
        }

        if (isConsole(source)) {
            sendMiniToPlayers(msg, perm, seeAll);
            return;
        }

        if ("global".equalsIgnoreCase(sectionKey)) {
            proxyChat.publishPlayer((Player) source, msg, "global");
            return;
        }

        Map<String, String> ctx = publicCtx(
                server,
                playerName,
                msg
        );
        String fmt = secString(sec, "format", "{player}: {message}");
        String rendered = Message.renderTemplateString(fmt, ctx);

        if (seeAll) {
            sendMessageToPermittedPlayers(rendered, "");
            return;
        }

        sendMessageToPermittedPlayers(rendered, perm);
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        Map<String, Object> sec = findSection(invocation.alias());
        if (sec == null || (!isPrivate(sec) && !isReply(sec))) {
            return CompletableFuture.completedFuture(List.of());
        }

        if (isReply(sec)) {
            return CompletableFuture.completedFuture(List.of());
        }

        List<String> names = Tensa.server.getAllPlayers().stream()
                .map(Player::getUsername)
                .toList();

        if (invocation.arguments().length == 0) {
            return CompletableFuture.completedFuture(names);
        }

        if (invocation.arguments().length == 1) {
            String typed = invocation.arguments()[0].toLowerCase(Locale.ROOT);
            List<String> out = names.stream()
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(typed))
                    .toList();
            return CompletableFuture.completedFuture(out);
        }

        return CompletableFuture.completedFuture(List.of());
    }

    private static void sendMessageToPermittedPlayers(String message, String permission) {
        if (permission == null || permission.isBlank()) {
            Tensa.server.getAllPlayers().forEach(player -> Message.send(player, message));
            Message.send(Tensa.server.getConsoleCommandSource(), message);
            return;
        }
        Tensa.server.getAllPlayers().stream()
                .filter(player -> player.hasPermission(permission))
                .forEach(player -> Message.send(player, message));
        Message.send(Tensa.server.getConsoleCommandSource(), message);
    }
}
