package ua.co.tensa.modules.chat;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import ua.co.tensa.Util;
import ua.co.tensa.config.Lang;
import ua.co.tensa.config.model.YamlAdapter;
import ua.co.tensa.modules.chat.data.ChatConfig;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ChatCommands implements SimpleCommand {

    // Access chats.yml via section maps
    private static YamlAdapter chatCfg = ChatConfig.get().adapter();

    /**
     * Read module flag dynamically to respect reload().
     */
    private static boolean isChatEnabled() {
        return Tensa.config != null && Tensa.config.isModuleEnabled("chat-manager");
    }

    /**
     * Normalize a command label: strip leading '/', trim.
     */
    private static String normalizeCmd(String cmd) {
        if (cmd == null) return null;
        String c = cmd.trim();
        if (c.startsWith("/")) c = c.substring(1);
        return c.trim();
    }

    /**
     * Safe String getter from a section map.
     */
    private static String secString(Map<String, Object> sec, String key, String def) {
        if (sec == null) return def;
        Object v = sec.get(key);
        if (v == null) return def;
        if (v instanceof String s) return s;
        return String.valueOf(v);
    }

    /**
     * Safe boolean getter from a section map.
     */
    private static boolean secBool(Map<String, Object> sec, String key, boolean def) {
        if (sec == null) return def;
        Object v = sec.get(key);
        if (v == null) return def;
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s.trim());
        return def;
    }

    /**
     * Produce a list of commands for a section.
     * Supports:
     * - commands: [a, b, c]
     * - command: "a b,c;d"
     */
    private static List<String> secCommands(Map<String, Object> sec) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (sec == null) return List.of();

        Object c = sec.get("commands");
        if (c instanceof Collection<?> col) {
            for (Object o : col) {
                if (o == null) continue;
                String s = normalizeCmd(String.valueOf(o));
                if (s != null && !s.isBlank()) out.add(s);
            }
        }

        String one = normalizeCmd(secString(sec, "command", ""));
        if (one != null && !one.isBlank()) {
            String[] tokens = one.split("[,;\\s]+");
            for (String t : tokens) {
                String s = normalizeCmd(t);
                if (s != null && !s.isBlank()) out.add(s);
            }
        }

        return new ArrayList<>(out);
    }

    /**
     * Reload configuration and re-register commands.
     */
    public static void reload() {
        ChatConfig.get().reloadCfg();
        chatCfg = ChatConfig.get().adapter();
        unregister();
        register();
    }

    /**
     * Register commands (no aliases), multiple names per section allowed.
     */
    public static void register() {
        if (!isChatEnabled()) return;

        for (String key : chatCfg.getKeys(false)) {
            Map<String, Object> sec = chatCfg.getSection(key);
            if (sec == null || sec.isEmpty()) continue;
            if (!secBool(sec, "enabled", true)) continue;

            List<String> cmds = secCommands(sec);
            if (cmds.isEmpty()) continue;

            for (String cmd : cmds) {
                Util.registerCommand(cmd, "", new ChatCommands());
            }
        }
    }

    /**
     * Unregister previously registered commands.
     */
    public static void unregister() {
        for (String key : chatCfg.getKeys(false)) {
            Map<String, Object> sec = chatCfg.getSection(key);
            if (sec == null || sec.isEmpty()) continue;

            for (String cmd : secCommands(sec)) {
                Util.unregisterCommand(cmd);
            }
        }
    }

    /**
     * Command execution entry-point.
     */
    @Override
    public void execute(Invocation invocation) {
        if (!isChatEnabled()) return;

        final String used = invocation.alias();

        String server;
        String playerName;
        if (invocation.source() instanceof Player p) {
            server = p.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse("");
            playerName = p.getUsername();
        } else {
            server = "";
            playerName = "";
        }

        for (String key : chatCfg.getKeys(false)) {
            Map<String, Object> sec = chatCfg.getSection(key);
            if (sec == null || sec.isEmpty()) continue;
            if (!secBool(sec, "enabled", true)) continue;

            List<String> cmds = secCommands(sec);
            if (cmds.isEmpty() || !cmds.contains(used)) continue;

            String type = secString(sec, "type", "public");
            if ("private".equalsIgnoreCase(type)) {
                handlePrivateChat(invocation, key, sec, server, playerName);
            } else {
                handlePublicChat(invocation, key, sec, server, playerName);
            }
            return;
        }
    }

    /**
     * Private chat handler: /pm <target> <message...>
     */
    private void handlePrivateChat(Invocation invocation, String key, Map<String, Object> sec, String server, String playerName) {
        CommandSource sender = invocation.source();
        boolean console = !(sender instanceof Player);

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

    /**
     * Public chat handler.
     */
    private void handlePublicChat(Invocation invocation, String key, Map<String, Object> sec, String server, String playerName) {

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

    /**
     * Tab-completion: for private chats, suggest online player names for the first arg.
     */
    @Override
    public CompletableFuture<List<String>> suggestAsync(final Invocation invocation) {
        final String used = invocation.alias();

        boolean isPrivate = false;
        for (String key : chatCfg.getKeys(false)) {
            Map<String, Object> sec = chatCfg.getSection(key);
            if (sec == null || sec.isEmpty()) continue;
            if (!secBool(sec, "enabled", true)) continue;

            List<String> cmds = secCommands(sec);
            if (cmds.isEmpty() || !cmds.contains(used)) continue;

            String type = secString(sec, "type", "public");
            if ("private".equalsIgnoreCase(type)) {
                isPrivate = true;
                break;
            }
        }

        if (!isPrivate) return CompletableFuture.completedFuture(List.of());

        List<String> names = Tensa.server.getAllPlayers().stream().map(Player::getUsername).toList();

        if (invocation.arguments().length == 0) {
            return CompletableFuture.completedFuture(names);
        }
        if (invocation.arguments().length == 1) {
            String typed = invocation.arguments()[0].toLowerCase(Locale.ROOT);
            List<String> sug = names.stream().filter(n -> n.toLowerCase(Locale.ROOT).startsWith(typed)).toList();
            return CompletableFuture.completedFuture(sug);
        }
        return CompletableFuture.completedFuture(List.of());
    }
}
