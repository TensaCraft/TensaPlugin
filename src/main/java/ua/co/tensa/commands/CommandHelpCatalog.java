package ua.co.tensa.commands;

import com.velocitypowered.api.command.SimpleCommand;
import ua.co.tensa.Message;
import ua.co.tensa.Util;
import ua.co.tensa.config.Lang;
import ua.co.tensa.modules.bridge.PMBridgeDebugCommand;
import ua.co.tensa.modules.chat.ChatCommands;
import ua.co.tensa.modules.meta.UserMetaCommand;
import ua.co.tensa.modules.playertime.PlayerTimeCommand;
import ua.co.tensa.modules.playertime.PlayerTimeTopCommand;
import ua.co.tensa.modules.queue.CommandQueueCommand;
import ua.co.tensa.modules.rcon.manager.RconManagerCommand;
import ua.co.tensa.modules.requests.RequestCommand;
import ua.co.tensa.modules.requests.RequestsModule;
import ua.co.tensa.modules.text.TextReaderCommand;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CommandHelpCatalog {

    private CommandHelpCatalog() {
    }

    public static List<HelpEntry> entries() {
        List<HelpEntry> entries = new ArrayList<>();
        for (Util.RegisteredCommand command : Util.getRegisteredCommands()) {
            HelpEntry entry = describe(command);
            if (entry != null) {
                entries.add(entry);
            }
        }

        entries.sort(Comparator
                .comparingInt(HelpEntry::sortOrder)
                .thenComparing(HelpEntry::usage, String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    public static String text(String key, String fallback) {
        if (key == null || key.isBlank()) {
            return fallback;
        }
        String value = Lang.LangConfig.getCleanText(key);
        return key.equals(value) ? fallback : value;
    }

    private static HelpEntry describe(Util.RegisteredCommand command) {
        if (command == null || command.handler() == null) {
            return null;
        }

        SimpleCommand handler = command.handler();
        if (handler instanceof HelpCommand && "tensahelp".equalsIgnoreCase(normalize(command.primary()))) {
            return null;
        }
        String display = displayName(command);
        String aliases = alternateNames(command, display);
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("command", display);

        String usageTemplate = "/{command}";
        String descriptionKey = null;
        String descriptionFallback = "Run /{command}.";
        int sortOrder = 900;

        if (handler instanceof HelpCommand) {
            descriptionKey = "help_desc_tensa";
            descriptionFallback = "Show help.";
            sortOrder = 10;
        } else if (handler instanceof ReloadCommand) {
            descriptionKey = "help_desc_tensareload";
            descriptionFallback = "Reload all plugin configurations.";
            sortOrder = 20;
        } else if (handler instanceof ModulesCommand) {
            descriptionKey = "help_desc_tensamodules";
            descriptionFallback = "Show all modules.";
            sortOrder = 30;
        } else if (handler instanceof PluginsCommand) {
            usageTemplate = "/{command} [-v]";
            descriptionKey = "help_desc_tpl";
            descriptionFallback = "Show installed proxy plugins.";
            sortOrder = 40;
        } else if (handler instanceof PlayerSendCommand) {
            usageTemplate = "/{command} <player|all> <server>";
            descriptionKey = "help_desc_psend";
            descriptionFallback = "Send a player to another server.";
            sortOrder = 50;
        } else if (handler instanceof PlaceholderParseCommand) {
            usageTemplate = "/{command} <text>";
            descriptionKey = "help_desc_tparse";
            descriptionFallback = "Parse placeholders in text.";
            sortOrder = 60;
        } else if (handler instanceof TensaInfoCommand) {
            descriptionKey = "help_desc_tinfo";
            descriptionFallback = "Show plugin information, modules, and commands.";
            sortOrder = 70;
        } else if (handler instanceof PlayerTimeCommand) {
            usageTemplate = "/{command} [player]";
            descriptionKey = "help_desc_tptime";
            descriptionFallback = "Show playing time for yourself or another player.";
            sortOrder = 100;
        } else if (handler instanceof PlayerTimeTopCommand) {
            descriptionKey = "help_desc_tptop";
            descriptionFallback = "Show top players by playing time.";
            sortOrder = 110;
        } else if (handler instanceof RconManagerCommand) {
            usageTemplate = "/{command} <server|all|reload> <command>";
            descriptionKey = "help_desc_rcon";
            descriptionFallback = "Execute an RCON command on one or more servers.";
            sortOrder = 120;
        } else if (handler instanceof UserMetaCommand) {
            usageTemplate = "/{command} <set|get|del|list> [player] <key> [value...] [--session]";
            descriptionKey = "help_desc_tmeta";
            descriptionFallback = "Manage temporary and persistent user metadata.";
            sortOrder = 130;
        } else if (handler instanceof PMBridgeDebugCommand) {
            descriptionKey = "help_desc_tpmdebug";
            descriptionFallback = "Show PM-Bridge debug information.";
            sortOrder = 140;
        } else if (handler instanceof CommandQueueCommand) {
            usageTemplate = "/{command} <player|uuid> <command...> [-t:seconds]";
            descriptionKey = "help_desc_tqueue";
            descriptionFallback = "Queue a console command until the target player is online.";
            sortOrder = 150;
        } else if (handler instanceof TextReaderCommand) {
            descriptionKey = "help_desc_text_reader";
            descriptionFallback = "Read the text file {command}.";
            sortOrder = 300;
        } else if (handler instanceof RequestCommand) {
            usageTemplate = "/{command} [args...]";
            descriptionKey = "help_desc_request_trigger";
            descriptionFallback = "Execute the request trigger from {file}.";
            placeholders.put("file", safe(RequestsModule.fileByTrigger(command.primary()), "request config"));
            sortOrder = 310;
        } else if (handler instanceof ChatCommands) {
            ChatCommands.ChatRoute route = ChatCommands.findRoute(display);
            boolean privateRoute = route != null && route.privateRoute();
            usageTemplate = privateRoute ? "/{command} <player> <message>" : "/{command} <message>";
            descriptionKey = privateRoute ? "help_desc_chat_private" : "help_desc_chat_public";
            descriptionFallback = privateRoute
                    ? "Send a private message through {command}."
                    : "Send a message to chat {command}.";
            placeholders.put("chat", route == null ? display : route.key());
            sortOrder = 320;
        }

        String usage = Message.renderTemplateString(usageTemplate, placeholders);
        String description = Message.renderTemplateString(text(descriptionKey, descriptionFallback), placeholders);
        return new HelpEntry(usage, description, aliases, sortOrder);
    }

    private static String displayName(Util.RegisteredCommand command) {
        SimpleCommand handler = command.handler();
        String primary = normalize(command.primary());
        String alias = normalize(command.alias());

        if (handler instanceof PlayerTimeCommand || handler instanceof PlayerTimeTopCommand) {
            return alias.isBlank() ? primary : alias;
        }

        return primary.isBlank() ? alias : primary;
    }

    private static String alternateNames(Util.RegisteredCommand command, String display) {
        List<String> names = new ArrayList<>();
        addAlternate(names, command.primary(), display);
        addAlternate(names, command.alias(), display);
        if (command.handler() instanceof HelpCommand && "tensa".equalsIgnoreCase(display)) {
            addAlternate(names, "tensahelp", display);
        }
        return String.join(", ", names);
    }

    private static void addAlternate(List<String> names, String candidate, String display) {
        String normalized = normalize(candidate);
        if (normalized.isBlank() || normalized.equalsIgnoreCase(display)) {
            return;
        }
        names.add("/" + normalized);
    }

    private static String normalize(String command) {
        if (command == null) {
            return "";
        }
        String normalized = command.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record HelpEntry(String usage, String description, String aliases, int sortOrder) {
    }
}
