package ua.co.tensa.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import ua.co.tensa.config.Config;
import ua.co.tensa.config.Lang;
import ua.co.tensa.modules.Modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public final class ReloadCommand implements SimpleCommand {

	@Override
	public void execute(final Invocation invocation) {
		CommandSource source = invocation.source();
		if (!hasPermission(invocation)) {
			Message.sendLang(source, Lang.no_perms);
			return;
		}
        String target = target(invocation.arguments());
        Tensa.server.getScheduler()
                .buildTask(Tensa.pluginContainer, () -> reload(source, target))
                .schedule();
	}

	@Override
	public boolean hasPermission(final Invocation invocation) {
		return canReload(invocation.source()::hasPermission, target(invocation.arguments()));
	}

	@Override
	public CompletableFuture<List<String>> suggestAsync(final Invocation invocation) {
		if (invocation.arguments().length > 1) {
            return CompletableFuture.completedFuture(List.of());
        }
        String prefix = invocation.arguments().length == 0
                ? ""
                : invocation.arguments()[0].toLowerCase(Locale.ROOT);
        List<String> candidates = new ArrayList<>();
        if (canReload(invocation.source()::hasPermission, "all")) {
            candidates.add("all");
        }
        for (String id : Modules.getEntries().keySet()) {
            if (canReload(invocation.source()::hasPermission, id)) {
                candidates.add(id);
            }
        }
        return CompletableFuture.completedFuture(candidates.stream()
                .filter(candidate -> candidate.startsWith(prefix))
                .sorted()
                .toList());
	}

    static boolean canReload(Predicate<String> permission, String target) {
        if (permission.test("tensa.reload")) {
            return true;
        }
        return target != null
                && !"all".equals(target)
                && permission.test("tensa.reload." + target);
    }

    private static String target(String[] arguments) {
        if (arguments == null || arguments.length == 0 || arguments[0] == null || arguments[0].isBlank()) {
            return "all";
        }
        return arguments[0].trim().toLowerCase(Locale.ROOT);
    }

    private static void reload(CommandSource source, String target) {
        if ("all".equals(target)) {
            if (Tensa.config == null) {
                Tensa.config = new Config();
            }
            List<String> failures = Tensa.reloadPlugin();
            if (failures.isEmpty()) {
                Message.sendLang(source, Lang.reload);
            } else {
                Message.privateMessage(source,
                        "<red>Reload completed with failures:</red> <white>"
                                + Message.escapeMiniMessage(String.join(", ", failures)) + "</white>");
            }
            return;
        }

        Modules.ReloadResult result = Modules.reloadModule(target);
        switch (result) {
            case RELOADED -> Message.privateMessage(source,
                    "<green>Module reloaded:</green> <white>" + Message.escapeMiniMessage(target) + "</white>");
            case RESTART_REQUIRED -> Message.privateMessage(source,
                    "<yellow>Configuration validated; restart required to apply:</yellow> <white>"
                            + Message.escapeMiniMessage(target) + "</white>");
            case NOT_FOUND -> Message.privateMessage(source,
                    "<red>Unknown module:</red> <white>" + Message.escapeMiniMessage(target) + "</white>");
            case DISABLED -> Message.privateMessage(source,
                    "<yellow>Module is disabled:</yellow> <white>" + Message.escapeMiniMessage(target) + "</white>");
            case FAILED -> Message.privateMessage(source,
                    "<red>Module reload failed; the previous runtime remains active:</red> <white>"
                            + Message.escapeMiniMessage(target) + "</white>");
        }
    }
}
