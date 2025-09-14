package ua.co.tensa.modules.chat;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import ua.co.tensa.Util;
import ua.co.tensa.config.Config;
import ua.co.tensa.config.Lang;
import ua.co.tensa.config.core.ConfigAdapter;
import ua.co.tensa.config.data.ChatYAML;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ChatCommands implements SimpleCommand {

    // We keep only the adapter for keys + section map access.
    private static ConfigAdapter chatCfg = ChatYAML.getInstance().adapter();

    /** Read module flag dynamically, so reload() takes effect. */
    private static boolean isChatEnabled() {
        return Config.getModules("chat-manager");
    }

    /** Normalize a command label (strip leading '/', trim). */
    private static String normalizeCmd(String cmd) {
        if (cmd == null) return null;
        String c = cmd.trim();
        if (c.startsWith("/")) c = c.substring(1);
        return c.trim();
    }

    /** Safe get string from section map with default. */
    private static String secString(Map<String, Object> sec, String key, String def) {
        if (sec == null) return def;
        Object v = sec.get(key);
        if (v == null) return def;
        if (v instanceof String s) return s;
        return String.valueOf(v);
    }

    /** Safe get boolean from section map with default. Accepts Boolean or "true"/"false". */
    private static boolean secBool(Map<String, Object> sec, String key, boolean def) {
        if (sec == null) return def;
        Object v = sec.get(key);
        if (v == null) return def;
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s.trim());
        return def;
    }

    /** Resolve command for a given top-level key via section map. Returns normalized or null. */
    private static String secCommand(Map<String, Object> sec) {
        String raw = secString(sec, "command", "");
        String cmd = normalizeCmd(raw);
        return (cmd == null || cmd.isBlank()) ? null : cmd;
    }

    public static void reload() {
        ChatYAML.getInstance().reload();
        chatCfg = ChatYAML.getInstance().adapter();
        unregister();
        register();
    }

    public static void register() {
        if (!isChatEnabled()) return;

        // Iterate top-level entries: global, staff, alert, pm, ...
        for (String key : chatCfg.getKeys(false)) {
            Map<String, Object> sec = chatCfg.getSection(key);
            if (sec == null || sec.isEmpty()) continue;

            boolean enabled = secBool(sec, "enabled", true);
            if (!enabled) continue;

            String cmd = secCommand(sec);
            if (cmd == null) continue;

            // Always provide a non-empty alias to avoid edge cases.
            String alias = "t" + cmd;

            // Register primary + alias
            Util.registerCommand(cmd, alias, new ChatCommands());
        }
    }

    public static void unregister() {
        // Unregister both primary and alias for every section we know about
        for (String key : chatCfg.getKeys(false)) {
            Map<String, Object> sec = chatCfg.getSection(key);
            if (sec == null || sec.isEmpty()) continue;

            String cmd = secCommand(sec);
            if (cmd == null) continue;

            Util.unregisterCommand(cmd);
            Util.unregisterCommand("t" + cmd);
        }
    }

    @Override
    public void execute(Invocation invocation) {
        if (!isChatEnabled()) return;

        final String usedLabel = invocation.alias(); // already without '/'

        String server;
        String playerName;
        if (invocation.source() instanceof Player p) {
            server = p.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse("");
            playerName = p.getUsername();
        } else {
            server = "";
            playerName = "";
        }

        // Find a matching section by command (primary or alias) and enabled=true
        for (String key : chatCfg.getKeys(false)) {
            Map<String, Object> sec = chatCfg.getSection(key);
            if (sec == null || sec.isEmpty()) continue;

            boolean enabled = secBool(sec, "enabled", true);
            if (!enabled) continue;

            String cmd = secCommand(sec);
            if (cmd == null) continue;

            if (!usedLabel.equals(cmd) && !usedLabel.equals("t" + cmd)) continue;

            String type = secString(sec, "type", "public");
            if ("private".equalsIgnoreCase(type)) {
                handlePrivateChat(invocation, key, sec, server, playerName);
            } else {
                handlePublicChat(invocation, key, sec, server, playerName);
            }
            return; // only one should match
        }
    }

    private void handlePrivateChat(Invocation invocation, String key, Map<String, Object> sec,
                                   String server, String playerName) {
        CommandSource sender = invocation.source();
        boolean console = !(sender instanceof Player);

        // Expect at least: /pm <target> <message...>
        if (invocation.arguments().length < 2) {
            Message.sendLang(sender, Lang.chat_usage, "{command}", invocation.alias());
            return;
        }

        String targetName = invocation.arguments()[0];
        Optional<Player> opt = Tensa.server.getPlayer(targetName);
        if (opt.isEmpty()) {
            Message.sendLang(sender, Lang.player_not_found, "{player}", targetName);
            return;
        }
        Player target = opt.get();

        String[] args = invocation.arguments();
        String msg = (args.length <= 1) ? "" : String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        if (console) {
            // Console: send direct message
            Message.send(target, msg);
            return;
        }

        Map<String, String> ctx = new HashMap<>();
        ctx.put("server", server);
        ctx.put("from", playerName);
        ctx.put("target", target.getUsername());
        ctx.put("message", msg);

        String toFmt = secString(sec, "to_format", "{from}: {message}");
        String fromFmt = secString(sec, "from_format", "{to}: {message}");

        String toMsg = Message.renderTemplateString(toFmt, ctx);
        Message.privateMessage(target, toMsg);

        ctx.put("to", target.getUsername());
        String fromMsg = Message.renderTemplateString(fromFmt, ctx);
        Message.privateMessage(sender, fromMsg);
    }

    private void handlePublicChat(Invocation invocation, String key, Map<String, Object> sec,
                                  String server, String playerName) {

        String perm = secString(sec, "permission", "");
        if (!perm.isEmpty() && !invocation.source().hasPermission(perm)) {
            Message.sendLang(invocation.source(), Lang.no_perms);
            return;
        }

        String msg = String.join(" ", invocation.arguments());

        Map<String, String> ctx = new HashMap<>();
        ctx.put("server", server);
        ctx.put("player", playerName);
        ctx.put("message", msg);

        String fmt = secString(sec, "format", "{player}: {message}");
        String rendered = Message.renderTemplateString(fmt, ctx);

        boolean seeAll = secBool(sec, "see_all", false);
        if (seeAll) {
            ChatModule.sendMessageToPermittedPlayers(rendered, "");
        } else {
            ChatModule.sendMessageToPermittedPlayers(rendered, perm);
        }
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(final Invocation invocation) {
        final String used = invocation.alias();

        // Completion only for private chat commands that match `used`
        boolean isPrivate = false;
        for (String key : chatCfg.getKeys(false)) {
            Map<String, Object> sec = chatCfg.getSection(key);
            if (sec == null || sec.isEmpty()) continue;

            if (!secBool(sec, "enabled", true)) continue;

            String cmd = secCommand(sec);
            if (cmd == null) continue;

            if (!used.equals(cmd) && !used.equals("t" + cmd)) continue;

            String type = secString(sec, "type", "public");
            if ("private".equalsIgnoreCase(type)) {
                isPrivate = true;
                break;
            }
        }

        if (!isPrivate) return CompletableFuture.completedFuture(List.of());

        List<String> names = Tensa.server.getAllPlayers().stream()
                .map(Player::getUsername)
                .toList();

        if (invocation.arguments().length == 0) {
            return CompletableFuture.completedFuture(names);
        }
        if (invocation.arguments().length == 1) {
            String typed = invocation.arguments()[0].toLowerCase(Locale.ROOT);
            List<String> sug = names.stream()
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(typed))
                    .toList();
            return CompletableFuture.completedFuture(sug);
        }
        return CompletableFuture.completedFuture(List.of());
    }
}
