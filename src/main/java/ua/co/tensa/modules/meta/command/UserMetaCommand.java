package ua.co.tensa.modules.meta.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import ua.co.tensa.config.Lang;
import ua.co.tensa.modules.meta.UserMetaModule;
import ua.co.tensa.modules.meta.UserMetaStore;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class UserMetaCommand implements SimpleCommand {
    private final UserMetaStore store;

    public UserMetaCommand(UserMetaStore store) {
        this.store = store;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource sender = invocation.source();
        String[] args = invocation.arguments();
        if (!sender.hasPermission("tensa.meta")) {
            Message.sendLang(sender, Lang.no_perms);
            return;
        }

        if (args.length == 0) {
            Message.sendLang(sender, Lang.meta_usage);
            return;
        }

        String sub = args[0].toLowerCase();
        int index = 1;
        UUID target;
        if (args.length >= 3 && !args[1].contains(":") && Tensa.server.getPlayer(args[1]).isPresent()) {
            // Admin form with player name
            if (!sender.hasPermission("tensa.meta.admin")) {
                Message.sendLang(sender, Lang.no_perms);
                return;
            }
            target = Tensa.server.getPlayer(args[1]).get().getUniqueId();
            index = 2;
        } else {
            if (!(sender instanceof Player)) {
                Message.sendLang(sender, Lang.meta_need_player);
                return;
            }
            target = ((Player) sender).getUniqueId();
        }

        switch (sub) {
            case "set": {
                if (args.length <= index + 1) {
                    Message.sendLang(sender, Lang.meta_usage);
                    return;
                }
                String key = args[index];
                String value = String.join(" ", Arrays.copyOfRange(args, index + 1, args.length));
                boolean sessionOnly = hasFlag(value, "--session");
                value = stripFlags(value);
                store.set(target, key, value, sessionOnly ? true : !store.getDefaultPersist());
                Message.sendLang(sender, Lang.meta_set_ok, "{key}", key, "{value}", value);
                break;
            }
            case "get": {
                if (args.length <= index) {
                    Message.sendLang(sender, Lang.meta_usage);
                    return;
                }
                String key = args[index];
                String value = store.get(target, key);
                Message.sendLang(sender, Lang.meta_get_ok, "{key}", key, "{value}", value);
                break;
            }
            case "del": {
                if (args.length <= index) {
                    Message.sendLang(sender, Lang.meta_usage);
                    return;
                }
                String key = args[index];
                boolean sessionOnly = (args.length > index + 1) && args[index + 1].equalsIgnoreCase("--session");
                store.delete(target, key, sessionOnly);
                Message.sendLang(sender, Lang.meta_deleted_ok, "{key}", key);
                break;
            }
            case "list": {
                var map = store.getAll(target);
                if (map.isEmpty()) {
                    Message.sendLang(sender, Lang.meta_no_meta);
                } else {
                    Message.sendLang(sender, Lang.meta_list_header);
                    map.forEach((k, v) -> Message.send(sender, " - <green>" + k + ":</green> <gray>" + v + "</gray>"));
                }
                break;
            }
            default:
                Message.sendLang(sender, Lang.meta_usage);
        }
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] a = invocation.arguments();
        if (a.length == 0) return CompletableFuture.completedFuture(List.of("set", "get", "del", "list"));
        String sub = a[0].toLowerCase();
        if (a.length == 1) return CompletableFuture.completedFuture(List.of("set", "get", "del", "list"));
        // arg1 may be player or key depending on subcommand
        if (a.length == 2) {
            // suggest online players as potential target
            var list = new java.util.ArrayList<String>();
            for (Player p : Tensa.server.getAllPlayers()) list.add(p.getUsername());
            return CompletableFuture.completedFuture(list);
        }
        // a.length >= 3: try to detect if arg1 is player
        java.util.Optional<Player> opt = Tensa.server.getPlayer(a[1]);
        UUID target = opt.map(Player::getUniqueId).orElseGet(() -> (invocation.source() instanceof Player pl ? pl.getUniqueId() : null));
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        if (target != null) {
            var keys = UserMetaModule.getStore().getAll(target).keySet();
            out.addAll(keys);
        }
        if (sub.equals("set") || sub.equals("del")) {
            out.add("--session");
        }
        return CompletableFuture.completedFuture(out);
    }

    private static boolean hasFlag(String value, String flag) {
        return value != null && value.contains(flag);
    }

    private static String stripFlags(String value) {
        if (value == null) return null;
        return value.replace("--session", "").trim();
    }
}
