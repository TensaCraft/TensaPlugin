package ua.co.tensa.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import ua.co.tensa.config.Lang;

public final class TensaInfoCommand implements SimpleCommand {
    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!hasPermission(invocation)) {
            Message.sendLang(source, Lang.no_perms);
            return;
        }
        String version = Tensa.pluginContainer.getDescription().getVersion().orElse("unknown");
        String name = Tensa.pluginContainer.getDescription().getName().orElse("Tensa");
        Message.privateMessage(source, "<gradient:#55FFFF:#C792EA>" + Message.escapeMiniMessage(name)
                + "</gradient> <#667085>•</#667085> <#AAB4CC>version</#AAB4CC> <#F4F7FF>"
                + Message.escapeMiniMessage(version) + "</#F4F7FF>");
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("tensa.info");
    }
}
