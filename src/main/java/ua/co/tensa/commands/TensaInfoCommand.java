package ua.co.tensa.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import ua.co.tensa.Util;
import ua.co.tensa.config.Lang;

import java.util.Comparator;
import java.util.List;

public class TensaInfoCommand implements SimpleCommand {

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!hasPermission(invocation)) {
            Message.sendLang(source, Lang.no_perms);
            return;
        }

        if (invocation.arguments().length > 0
                && "communications".equalsIgnoreCase(invocation.arguments()[0])) {
            showCommunications(source);
            return;
        }

        String version = Tensa.pluginContainer.getDescription().getVersion().orElse("unknown");
        String name = Tensa.pluginContainer.getDescription().getName().orElse("Tensa");

        Message.privateMessage(source, "<aqua>" + name + "</aqua> <gray>v</gray><green>" + version + "</green>");

        // Modules section
        var entries = ua.co.tensa.modules.Modules.getEntries();
        Message.privateMessage(source, "<gold>Modules</gold> <gray>(" + entries.size() + ")</gray>:");
        for (var e : entries.values()) {
            String title = e.title();
            String status = e.isEnabled() ? "<green>enabled</green>" : "<red>disabled</red>";
            Message.privateMessage(source, "  <yellow>•</yellow> <white>" + title + "</white> <gray>-</gray> " + status);
        }

        // Commands section (from Util registry), grouped by module
        var cmds = Util.getRegisteredCommands();
        cmds.sort(Comparator.comparing(Util.RegisteredCommand::primary));
        java.util.Map<String, java.util.List<ua.co.tensa.Util.RegisteredCommand>> byModule = new java.util.LinkedHashMap<>();
        for (var c : cmds) {
            String module = (c.module() == null || c.module().isBlank()) ? "core" : c.module();
            byModule.computeIfAbsent(module, k -> new java.util.ArrayList<>()).add(c);
        }
        Message.privateMessage(source, "<gold>Commands</gold> <gray>(" + cmds.size() + ")</gray>:");
        for (var entry : byModule.entrySet()) {
            String mod = entry.getKey();
            var list = entry.getValue();
            Message.privateMessage(source, "<yellow>•</yellow> <white>" + capitalizeWords(mod.replace('-', ' ')) + "</white> <gray>(" + list.size() + ")</gray>");
            list.sort(Comparator.comparing(Util.RegisteredCommand::primary));
            for (var c : list) {
                String primary = c.primary() == null ? "" : c.primary().trim();
                String aliasVal = c.alias() == null ? "" : c.alias().trim();
                if (c.handler() instanceof HelpCommand && "tensahelp".equalsIgnoreCase(primary)) {
                    continue;
                }
                boolean showAlias = !aliasVal.isBlank() && !aliasVal.equalsIgnoreCase(primary);
                if (primary.isBlank()) {
                    // Skip unnamed commands (e.g., chat routes that have no explicit command binding)
                    continue;
                }
                String alias = showAlias ? (" <gray>(alias:</gray> <yellow>" + aliasVal + "</yellow><gray>)</gray>") : "";
                Message.privateMessage(source, "  <yellow>/" + primary + "</yellow>" + alias);
            }
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("tensa.info");
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (!hasPermission(invocation) || invocation.arguments().length > 1) {
            return List.of();
        }
        String prefix = invocation.arguments().length == 0 ? "" : invocation.arguments()[0].toLowerCase(java.util.Locale.ROOT);
        return "communications".startsWith(prefix) ? List.of("communications") : List.of();
    }

    private static void showCommunications(CommandSource source) {
        var snapshot = ua.co.tensa.modules.discord.CommunicationsModule.diagnosticsSnapshot();
        Message.privateMessage(source, "<gold>Communications</gold> <gray>safe runtime snapshot</gray>");
        Message.privateMessage(source, "  <yellow>runtime</yellow>=<white>" + snapshot.runtimeState()
                + "</white> <yellow>jda</yellow>=<white>" + snapshot.jdaState()
                + "</white> <yellow>slash</yellow>=<white>" + snapshot.slashState() + "</white>");
        Message.privateMessage(source, "  <yellow>chat_transport</yellow>=<white>"
                + snapshot.chatTransportState() + "</white>");
        Message.privateMessage(source, "  <yellow>storage</yellow>=<white>" + snapshot.storageBackend()
                + "</white> <yellow>queues</yellow>=<white>in:" + snapshot.inboundQueueDepth()
                + ",out:" + snapshot.outboundQueueDepth()
                + ",roles:" + snapshot.roleQueueDepth() + "</white>");
        Message.privateMessage(source, "  <yellow>drops</yellow>=<white>" + snapshot.drops()
                + "</white> <yellow>retries</yellow>=<white>" + snapshot.retries()
                + "</white> <yellow>delivery_failures</yellow>=<white>" + snapshot.deliveryFailures() + "</white>");
        Message.privateMessage(source, "  <yellow>reconnects</yellow>=<white>" + snapshot.reconnects()
                + "</white> <yellow>latency_ms</yellow>=<white>" + snapshot.lastObservedLatencyMillis()
                + "</white> <yellow>last_failure</yellow>=<white>" + snapshot.lastFailureClass() + "</white>");
        var resources = snapshot.resources();
        Message.privateMessage(source, "  <yellow>heap</yellow>=<white>" + resources.heapPercent() + "%"
                + "</white> <yellow>state</yellow>=<white>chat:" + resources.chatStateEntries()
                + ",reply:" + resources.privateReplyEntries()
                + ",codes:" + resources.linkCodes()
                + ",links:" + resources.linkIndexEntries() + "</white>");
        Message.privateMessage(source, "  <yellow>chat_components</yellow>=<white>clickable_urls:"
                + resources.clickableUrlsRendered() + "</white>");
        Message.privateMessage(source, "  <yellow>scheduler</yellow>=<white>jobs:" + resources.schedulerJobs()
                + ",queue:" + resources.schedulerQueueDepth()
                + ",rejected:" + resources.schedulerRejected() + "</white>");
    }

    private static String capitalizeWords(String input) {
        String[] words = input.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            sb.append(' ');
        }
        return sb.toString().trim();
    }
}
