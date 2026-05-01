package ua.co.tensa.modules.queue;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import ua.co.tensa.Message;
import ua.co.tensa.config.Lang;
import ua.co.tensa.modules.queue.data.CommandQueueConfig;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class CommandQueueCommand implements SimpleCommand {
    private static final List<String> ADMIN_SUBCOMMANDS = List.of("add", "list", "read", "remove", "clear", "run", "stats");

    private final CommandQueueManager manager;

    public CommandQueueCommand(CommandQueueManager manager) {
        this.manager = manager;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        String commandName = invocation.alias() == null || invocation.alias().isBlank() ? "tqueue" : invocation.alias();

        if (!source.hasPermission("tensa.queue")) {
            Message.sendLang(source, Lang.no_perms);
            return;
        }

        if (args.length == 0) {
            sendUsage(source, commandName);
            return;
        }

        String first = args[0].toLowerCase(Locale.ROOT);
        if (isAdminSubcommand(first)) {
            if (!source.hasPermission("tensa.queue.admin")) {
                Message.sendLang(source, Lang.no_perms);
                return;
            }
            handleAdmin(source, args, commandName);
            return;
        }

        addEntry(source, args, sourceName(source));
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] args = invocation.arguments();
        boolean admin = invocation.source().hasPermission("tensa.queue.admin");
        if (args.length == 0) {
            return CompletableFuture.completedFuture(rootSuggestions("", admin));
        }
        if (args.length == 1) {
            return CompletableFuture.completedFuture(rootSuggestions(args[0], admin));
        }

        String first = args[0].toLowerCase(Locale.ROOT);
        if (admin && isAdminSubcommand(first)) {
            return CompletableFuture.completedFuture(adminSuggestions(first, args));
        }
        return CompletableFuture.completedFuture(addSuggestions(args));
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("tensa.queue");
    }

    private void handleAdmin(CommandSource source, String[] args, String commandName) {
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "add" -> {
                if (args.length < 3) {
                    sendUsage(source, commandName);
                    return;
                }
                addEntry(source, Arrays.copyOfRange(args, 1, args.length), sourceName(source));
            }
            case "list" -> listEntries(source, args.length >= 2 ? args[1] : "");
            case "read" -> readEntry(source, args, commandName);
            case "remove" -> removeEntry(source, args, commandName);
            case "clear" -> clearEntries(source, args, commandName);
            case "run" -> runEntry(source, args, commandName);
            case "stats" -> showStats(source);
            default -> sendUsage(source, commandName);
        }
    }

    private void addEntry(CommandSource source, String[] args, String createdBy) {
        ParsedAddCommand parsed = parseAddCommand(args);
        if (parsed.errorKey() != null) {
            Message.sendLang(source, parsed.errorKey());
            return;
        }
        if (manager.stats().totalEntries() >= CommandQueueConfig.get().maxEntries) {
            Message.sendLang(source, Lang.queue_limit_reached);
            return;
        }

        QueuedCommandEntry entry = manager.enqueue(parsed.target(), parsed.command(), parsed.delaySeconds(), createdBy);
        int dispatched = manager.dispatchDue();
        Message.sendLang(source, Lang.queue_added,
                "{id}", Long.toString(entry.id()),
                "{target}", entry.displayTarget(),
                "{delay}", Long.toString(entry.delaySeconds()));
        if (dispatched > 0) {
            Message.sendLang(source, Lang.queue_dispatched_ready, "{count}", Integer.toString(dispatched));
        }
    }

    private void listEntries(CommandSource source, String selector) {
        List<QueuedCommandEntry> entries = manager.snapshot(selector);
        if (entries.isEmpty()) {
            Message.sendLang(source, Lang.queue_list_empty);
            return;
        }

        Message.sendLang(source, Lang.queue_list_header,
                "{count}", Integer.toString(entries.size()),
                "{filter}", selector == null || selector.isBlank() ? "*" : selector);

        long now = System.currentTimeMillis();
        for (QueuedCommandEntry entry : entries.stream()
                .sorted(Comparator.comparingLong(QueuedCommandEntry::id))
                .toList()) {
            Message.sendTemplate(source,
                    "<gray>#</gray><yellow>{id}</yellow> <white>{target}</white> <dark_gray>[</dark_gray><aqua>{remaining}s</aqua><dark_gray>]</dark_gray> <gray>{command}</gray>",
                    Map.of(
                            "id", Long.toString(entry.id()),
                            "target", entry.displayTarget(),
                            "remaining", Long.toString(entry.remainingSeconds(now)),
                            "command", Message.escapeMiniMessage(entry.preview())
                    ));
        }
    }

    private void readEntry(CommandSource source, String[] args, String commandName) {
        Long id = parseId(args, 1);
        if (id == null) {
            sendUsage(source, commandName);
            return;
        }
        QueuedCommandEntry entry = manager.get(id);
        if (entry == null) {
            Message.sendLang(source, Lang.queue_not_found, "{id}", Long.toString(id));
            return;
        }

        Message.sendTemplate(source,
                "<gold>Queue #</gold><yellow>{id}</yellow> <gray>target:</gray> <white>{target}</white>",
                Map.of("id", Long.toString(entry.id()), "target", entry.displayTarget()));
        Message.sendTemplate(source,
                "<gray>Created by:</gray> <white>{createdBy}</white> <gray>at</gray> <white>{createdAt}</white>",
                Map.of(
                        "createdBy", Message.escapeMiniMessage(blankToDash(entry.createdBy())),
                        "createdAt", Instant.ofEpochMilli(entry.createdAtMillis()).toString()
                ));
        Message.sendTemplate(source,
                "<gray>Due at:</gray> <white>{dueAt}</white> <gray>(delay:</gray> <aqua>{delay}s</aqua><gray>)</gray>",
                Map.of(
                        "dueAt", Instant.ofEpochMilli(entry.notBeforeMillis()).toString(),
                        "delay", Long.toString(entry.delaySeconds())
                ));
        Message.sendTemplate(source,
                "<gray>Command:</gray> <white>{command}</white>",
                Map.of("command", Message.escapeMiniMessage(entry.command())));
    }

    private void removeEntry(CommandSource source, String[] args, String commandName) {
        Long id = parseId(args, 1);
        if (id == null) {
            sendUsage(source, commandName);
            return;
        }
        if (!manager.remove(id)) {
            Message.sendLang(source, Lang.queue_not_found, "{id}", Long.toString(id));
            return;
        }
        Message.sendLang(source, Lang.queue_removed, "{id}", Long.toString(id));
    }

    private void clearEntries(CommandSource source, String[] args, String commandName) {
        if (args.length < 2) {
            sendUsage(source, commandName);
            return;
        }
        int removed = manager.clear(args[1]);
        Message.sendLang(source, Lang.queue_cleared,
                "{count}", Integer.toString(removed),
                "{target}", args[1]);
    }

    private void runEntry(CommandSource source, String[] args, String commandName) {
        Long id = parseId(args, 1);
        if (id == null) {
            sendUsage(source, commandName);
            return;
        }
        CommandQueueManager.DispatchResult result = manager.dispatchNow(id);
        switch (result.status()) {
            case NOT_FOUND -> Message.sendLang(source, Lang.queue_not_found, "{id}", Long.toString(id));
            case TARGET_OFFLINE -> Message.sendLang(source, Lang.queue_run_offline, "{id}", Long.toString(id));
            case DISPATCHED -> Message.sendLang(source, Lang.queue_run_ok,
                    "{id}", Long.toString(id),
                    "{player}", result.playerName());
        }
    }

    private void showStats(CommandSource source) {
        CommandQueueManager.QueueStats stats = manager.stats();
        Message.sendLang(source, Lang.queue_stats,
                "{total}", Integer.toString(stats.totalEntries()),
                "{due}", Integer.toString(stats.dueEntries()),
                "{online}", Integer.toString(stats.onlineEligibleEntries()));
    }

    private ParsedAddCommand parseAddCommand(String[] args) {
        if (args.length == 0) {
            return ParsedAddCommand.error(Lang.queue_target_required);
        }
        if (args.length == 1) {
            return ParsedAddCommand.error(Lang.queue_command_required);
        }

        String target = args[0];
        List<String> parts = new ArrayList<>(Arrays.asList(args).subList(1, args.length));
        long delaySeconds = 0L;
        if (!parts.isEmpty()) {
            String last = parts.get(parts.size() - 1);
            if (last.startsWith("-t:")) {
                String raw = last.substring(3);
                try {
                    delaySeconds = Long.parseLong(raw);
                    if (delaySeconds < 0L) {
                        return ParsedAddCommand.error(Lang.queue_invalid_delay);
                    }
                    parts.remove(parts.size() - 1);
                } catch (NumberFormatException e) {
                    return ParsedAddCommand.error(Lang.queue_invalid_delay);
                }
            }
        }

        String command = String.join(" ", parts).trim();
        if (command.isBlank()) {
            return ParsedAddCommand.error(Lang.queue_command_required);
        }

        return new ParsedAddCommand(target, command, delaySeconds, null);
    }

    private List<String> rootSuggestions(String input, boolean admin) {
        LinkedHashSet<String> suggestions = new LinkedHashSet<>();
        suggestions.addAll(manager.targetSuggestions());
        if (admin) {
            suggestions.addAll(ADMIN_SUBCOMMANDS);
        }
        return filterPrefix(suggestions, input);
    }

    private List<String> addSuggestions(String[] args) {
        String last = args[args.length - 1];
        if (args.length == 1) {
            return filterPrefix(manager.targetSuggestions(), last);
        }
        if (last.startsWith("-t:")) {
            return filterPrefix(delaySuggestions(), last);
        }
        return List.of("-t:0", "-t:5", "-t:60", "-t:300");
    }

    private List<String> adminSuggestions(String sub, String[] args) {
        return switch (sub) {
            case "add" -> {
                if (args.length == 2) {
                    yield filterPrefix(manager.targetSuggestions(), args[1]);
                }
                String last = args[args.length - 1];
                yield last.startsWith("-t:") ? filterPrefix(delaySuggestions(), last) : List.of("-t:0", "-t:5", "-t:60", "-t:300");
            }
            case "list", "clear" -> args.length == 2
                    ? filterPrefix(manager.targetSuggestions(), args[1])
                    : List.of();
            case "read", "remove", "run" -> args.length == 2
                    ? filterPrefix(manager.idSuggestions(), args[1])
                    : List.of();
            default -> List.of();
        };
    }

    private List<String> delaySuggestions() {
        return List.of("-t:0", "-t:5", "-t:30", "-t:60", "-t:300", "-t:900");
    }

    private List<String> filterPrefix(Iterable<String> values, String prefix) {
        String normalized = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            if (normalized.isBlank() || value.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                out.add(value);
            }
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    private boolean isAdminSubcommand(String value) {
        return ADMIN_SUBCOMMANDS.contains(value);
    }

    private Long parseId(String[] args, int index) {
        if (args.length <= index) {
            return null;
        }
        try {
            return Long.parseLong(args[index]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String sourceName(CommandSource source) {
        if (source instanceof Player player) {
            return player.getUsername();
        }
        return "console";
    }

    private void sendUsage(CommandSource source, String commandName) {
        Message.sendLang(source, Lang.queue_usage, "{command}", commandName);
    }

    private record ParsedAddCommand(String target, String command, long delaySeconds, Lang errorKey) {
        static ParsedAddCommand error(Lang key) {
            return new ParsedAddCommand("", "", 0L, key);
        }
    }
}
