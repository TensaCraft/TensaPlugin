package ua.co.tensa.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.plugin.PluginContainer;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import ua.co.tensa.config.Lang;

import java.util.Collection;
import java.util.stream.Collectors;

public class PluginsCommand implements SimpleCommand {
    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        if (!hasPermission(invocation)) {
            Message.sendLang(source, Lang.no_perms);
            return;
        }

        Collection<PluginContainer> plugins = Tensa.server.getPluginManager().getPlugins();
        int pluginCount = plugins.size();

        if (args.length == 1 && args[0].equals("-v")) {
            String pluginList = plugins.stream()
                    .map(plugin -> {
                        boolean loaded = plugin.getInstance().isPresent();
                        String name = plugin.getDescription().getName().orElse("Unknown");
                        String version = plugin.getDescription().getVersion().orElse("Unknown");
                        String color = loaded ? "<gold>" : "<red>";
                        return color + name + "</" + (loaded ? "gold" : "red") + "> <gray>" + version + "</gray>";
                    })
                    .collect(Collectors.joining(", "));
            Message.send(source, "<green>Plugins:</green> " + pluginList);
            return;
        }

        String pluginList = plugins.stream()
                .map(plugin -> {
                    boolean loaded = plugin.getInstance().isPresent();
                    String name = plugin.getDescription().getName().orElse("Unknown");
                    String color = loaded ? "<gold>" : "<red>";
                    return color + name + "</" + (loaded ? "gold" : "red") + ">";
                })
                .collect(Collectors.joining(", "));
        Message.send(source, "<green>Plugins (" + pluginCount + "):</green> " + pluginList);
    }

    @Override
    public boolean hasPermission(final Invocation invocation) {
        return invocation.source().hasPermission("tensa.plugins");
    }
}
