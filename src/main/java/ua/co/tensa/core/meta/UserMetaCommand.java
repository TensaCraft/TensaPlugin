package ua.co.tensa.core.meta;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import ua.co.tensa.config.Lang;
import ua.co.tensa.core.user.UserProfile;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class UserMetaCommand implements SimpleCommand {
    private static final List<String> SUBCOMMANDS = List.of("set", "get", "del", "list");

    private final UserMetaService service;

    public UserMetaCommand(UserMetaService service) {
        this.service = service;
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

        String sub = args[0].toLowerCase(java.util.Locale.ROOT);
        if (!SUBCOMMANDS.contains(sub)) {
            Message.sendLang(sender, Lang.meta_usage);
            return;
        }

        TargetResolution target = resolveTarget(sender, args, "list".equals(sub));
        if (target.error() != null) {
            if (target.missingPlayerName().isBlank()) {
                Message.sendLang(sender, target.error());
            } else {
                Message.sendLang(sender, target.error(), "{player}", Message.escapeMiniMessage(target.missingPlayerName()));
            }
            return;
        }

        switch (sub) {
            case "set" -> set(sender, args, target);
            case "get" -> get(sender, args, target);
            case "del" -> delete(sender, args, target);
            case "list" -> list(sender, target);
            default -> Message.sendLang(sender, Lang.meta_usage);
        }
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            return CompletableFuture.completedFuture(SUBCOMMANDS);
        }

        if (args.length == 2) {
            java.util.ArrayList<String> out = new java.util.ArrayList<>();
            for (Player player : Tensa.server.getAllPlayers()) {
                out.add(player.getUsername());
            }
            if (invocation.source() instanceof Player player) {
                out.addAll(service.getCached(player.getUniqueId()).keySet());
            }
            return CompletableFuture.completedFuture(out);
        }

        UUID target = resolveSuggestionTarget(invocation.source(), args[1]);
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        if (target != null) {
            out.addAll(service.getCached(target).keySet());
        }
        if ("set".equalsIgnoreCase(args[0]) || "del".equalsIgnoreCase(args[0])) {
            out.add("--session");
        }
        return CompletableFuture.completedFuture(out);
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("tensa.meta");
    }

    private void set(CommandSource sender, String[] args, TargetResolution target) {
        if (args.length <= target.argIndex() + 1) {
            Message.sendLang(sender, Lang.meta_usage);
            return;
        }
        String key = args[target.argIndex()];
        String value = String.join(" ", Arrays.copyOfRange(args, target.argIndex() + 1, args.length));
        boolean sessionOnly = hasFlag(value, "--session");
        value = stripFlags(value);
        service.set(target.uuid(), key, value, sessionOnly || !service.defaultPersist());
        Message.sendLang(sender, Lang.meta_set_ok,
                "{key}", Message.escapeMiniMessage(key),
                "{value}", Message.escapeMiniMessage(value));
    }

    private void get(CommandSource sender, String[] args, TargetResolution target) {
        if (args.length <= target.argIndex()) {
            Message.sendLang(sender, Lang.meta_usage);
            return;
        }
        String key = args[target.argIndex()];
        service.getAsync(target.uuid(), key)
                .thenAccept(value -> schedule(() ->
                        Message.sendLang(sender, Lang.meta_get_ok,
                                "{key}", Message.escapeMiniMessage(key),
                                "{value}", Message.escapeMiniMessage(value))))
                .exceptionally(ex -> {
                    schedule(() -> Message.sendLang(sender, Lang.unknown_error));
                    Message.error("UserMeta get failed: " + ex.getMessage());
                    return null;
                });
    }

    private void delete(CommandSource sender, String[] args, TargetResolution target) {
        if (args.length <= target.argIndex()) {
            Message.sendLang(sender, Lang.meta_usage);
            return;
        }
        String key = args[target.argIndex()];
        boolean sessionOnly = (args.length > target.argIndex() + 1) && args[target.argIndex() + 1].equalsIgnoreCase("--session");
        service.delete(target.uuid(), key, sessionOnly);
        Message.sendLang(sender, Lang.meta_deleted_ok, "{key}", Message.escapeMiniMessage(key));
    }

    private void list(CommandSource sender, TargetResolution target) {
        service.getAllAsync(target.uuid())
                .thenAccept(map -> schedule(() -> {
                    if (map.isEmpty()) {
                        Message.sendLang(sender, Lang.meta_no_meta);
                        return;
                    }
                    Message.sendLang(sender, Lang.meta_list_header);
                    map.forEach((key, value) -> Message.send(sender,
                            " - <green>" + Message.escapeMiniMessage(key) + ":</green> <gray>" + Message.escapeMiniMessage(value) + "</gray>"));
                }))
                .exceptionally(ex -> {
                    schedule(() -> Message.sendLang(sender, Lang.unknown_error));
                    Message.error("UserMeta list failed: " + ex.getMessage());
                    return null;
                });
    }

    private TargetResolution resolveTarget(CommandSource sender, String[] args, boolean listCommand) {
        boolean canHaveExplicitTarget = args.length >= (listCommand ? 2 : 3);
        if (canHaveExplicitTarget) {
            String candidate = args[1];
            UUID uuid = resolveUser(candidate);
            if (uuid != null) {
                if (requiresAdmin(sender, uuid) && !sender.hasPermission("tensa.meta.admin")) {
                    return TargetResolution.error(Lang.no_perms);
                }
                return new TargetResolution(uuid, 2, null, "");
            }
            if (!(sender instanceof Player)) {
                return TargetResolution.error(Lang.player_not_found, candidate);
            }
        }

        if (sender instanceof Player player) {
            return new TargetResolution(player.getUniqueId(), 1, null, "");
        }
        return TargetResolution.error(Lang.meta_need_player);
    }

    private UUID resolveUser(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Optional<Player> online = Tensa.server.getPlayer(value);
        if (online.isPresent()) {
            return online.get().getUniqueId();
        }
        if (Tensa.userData == null) {
            return null;
        }
        return Tensa.userData.findUser(value).map(UserProfile::uuid).orElse(null);
    }

    private UUID resolveSuggestionTarget(CommandSource source, String value) {
        UUID resolved = resolveUser(value);
        if (resolved != null) {
            return resolved;
        }
        return source instanceof Player player ? player.getUniqueId() : null;
    }

    private boolean requiresAdmin(CommandSource sender, UUID target) {
        return !(sender instanceof Player player) || !player.getUniqueId().equals(target);
    }

    private static boolean hasFlag(String value, String flag) {
        return value != null && value.contains(flag);
    }

    private static String stripFlags(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("--session", "").trim();
    }

    private static void schedule(Runnable task) {
        Tensa.server.getScheduler()
                .buildTask(Tensa.pluginContainer, task)
                .schedule();
    }

    private record TargetResolution(UUID uuid, int argIndex, Lang error, String missingPlayerName) {
        static TargetResolution error(Lang key) {
            return new TargetResolution(null, 1, key, "");
        }

        static TargetResolution error(Lang key, String missingPlayerName) {
            return new TargetResolution(null, 1, key, missingPlayerName == null ? "" : missingPlayerName);
        }
    }
}
