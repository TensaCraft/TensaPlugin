package ua.co.tensa.commands;

import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import ua.co.tensa.config.Lang;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.plugin.PluginContainer;

import java.util.Collection;
import java.util.stream.Collectors;

public class PluginsCommand implements SimpleCommand {
    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        if (!hasPermission(invocation)) {
            source.sendMessage(Lang.no_perms.get());
            return;
        }
        // отримуємо всі плагіни як PluginContainer
        Collection<PluginContainer> plugins = Tensa.server.getPluginManager().getPlugins();
        int pluginCount = plugins.size();

        if (args.length == 1 && args[0].equals("-v")) {
            // із версіями
            String pluginList = plugins.stream()
                    .map(plugin -> {
                        boolean loaded = plugin.getInstance().isPresent();
                        String name = plugin.getDescription().getName().orElse("Unknown");
                        String version = plugin.getDescription().getVersion().orElse("Unknown");
                        String color = loaded ? "&6" : "&c";
                        return color + name + " &7" + version;
                    })
                    .collect(Collectors.joining(", "));
            source.sendMessage(Message.convert("&aPlugins: " + pluginList));
            return;
        }

        // без версій
        String pluginList = plugins.stream()
                .map(plugin -> {
                    boolean loaded = plugin.getInstance().isPresent();
                    String name = plugin.getDescription().getName().orElse("Unknown");
                    String color = loaded ? "&6" : "&c";
                    return color + name;
                })
                .collect(Collectors.joining(", "));
        source.sendMessage(Message.convert("&aPlugins (" + pluginCount + "): " + pluginList));
    }

    @Override
    public boolean hasPermission(final Invocation invocation) {
        return invocation.source().hasPermission("tensa.plugins");
    }
}
