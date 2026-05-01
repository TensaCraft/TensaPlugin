package ua.co.tensa.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import ua.co.tensa.Message;
import ua.co.tensa.config.Lang;
import ua.co.tensa.modules.ModuleEntry;
import ua.co.tensa.modules.Modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ModulesCommand implements SimpleCommand {
    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!hasPermission(invocation)) {
            Message.sendLang(source, Lang.no_perms);
            return;
        }

        visibleModuleStatuses(Modules.getEntries()).forEach(module -> {
            String status = module.enabled() ? Lang.enabled.getClean() : Lang.disabled.getClean();
            Message.sendLang(source, Lang.module_status, "{module}", module.title(), "{status}", status);
        });
    }

    @Override
    public boolean hasPermission(final Invocation invocation) {
        return invocation.source().hasPermission("tensa.modules");
    }

    static List<ModuleStatus> visibleModuleStatuses(Map<String, ModuleEntry> registry) {
        List<ModuleStatus> modules = new ArrayList<>();
        if (registry == null || registry.isEmpty()) {
            return modules;
        }
        for (Map.Entry<String, ModuleEntry> entry : registry.entrySet()) {
            ModuleEntry module = entry.getValue();
            if (module == null) {
                continue;
            }
            modules.add(new ModuleStatus(module.id(), module.title(), module.isEnabled()));
        }
        return modules;
    }

    record ModuleStatus(String id, String title, boolean enabled) {
    }
}
