package ua.co.tensa.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import ua.co.tensa.Message;
import ua.co.tensa.config.Lang;

import java.util.List;
import java.util.Map;

public class HelpCommand implements SimpleCommand {

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!hasPermission(invocation)) {
            Message.sendLang(source, Lang.no_perms);
            return;
        }

        Message.privateMessage(source, CommandHelpCatalog.text("help_header", "<gold>Available commands:</gold>"));

        List<CommandHelpCatalog.HelpEntry> entries = CommandHelpCatalog.entries();
        if (entries.isEmpty()) {
            Message.privateMessage(source, CommandHelpCatalog.text("help_empty", "<yellow>No commands registered.</yellow>"));
            return;
        }

        String lineFormat = CommandHelpCatalog.text(
                "help_command_format",
                "<gold>{usage}</gold>{aliases} <gray>-</gray> <green>{description}</green>"
        );
        String aliasFormat = CommandHelpCatalog.text(
                "help_alias_format",
                " <dark_gray>(</dark_gray><gray>alias:</gray> <yellow>{aliases}</yellow><dark_gray>)</dark_gray>"
        );

        for (CommandHelpCatalog.HelpEntry entry : entries) {
            String aliases = entry.aliases().isBlank()
                    ? ""
                    : Message.renderTemplateString(aliasFormat, Map.of("aliases", entry.aliases()));
            Message.privateMessage(source, Message.renderTemplateString(lineFormat, Map.of(
                    "usage", entry.usage(),
                    "aliases", aliases,
                    "description", entry.description()
            )));
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("tensa.help");
    }
}
