package ua.co.tensa.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import ua.co.tensa.config.Lang;

import java.util.List;

public class ModulesCommand implements SimpleCommand {
    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!hasPermission(invocation)) {
            Message.sendLang(source, Lang.no_perms);
            return;
        }

        List<String> allModules = Tensa.config.getModules();
        allModules.forEach(module -> {
            String moduleName = capitalizeWords(module.toUpperCase().replace('-', ' '));
            String status = Tensa.config.isModuleEnabled(module) ? Lang.enabled.getClean() : Lang.disabled.getClean();
            Message.sendLang(source, Lang.module_status, "{module}", moduleName, "{status}", status);
        });
    }

    @Override
    public boolean hasPermission(final Invocation invocation) {
        return invocation.source().hasPermission("tensa.modules");
    }

    private static String capitalizeWords(String input) {
        String[] words = input.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            sb.append(word.substring(0, 1).toUpperCase()).append(word.substring(1).toLowerCase()).append(" ");
        }
        return sb.toString().trim();
    }
}
