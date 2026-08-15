package ua.co.tensa.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import ua.co.tensa.Message;
import ua.co.tensa.config.Lang;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Single command tree for all Tensa administration commands. */
public final class TensaCommand implements SimpleCommand {
    private final Map<String, SimpleCommand> children = Map.of(
            "help", new HelpCommand(),
            "info", new TensaInfoCommand(),
            "modules", new ModulesCommand(),
            "reload", new ReloadCommand()
    );

    static Set<String> childNames() {
        return Set.of("help", "info", "modules", "reload");
    }

    @Override
    public void execute(Invocation invocation) {
        String[] arguments = invocation.arguments();
        String childName = arguments.length == 0 ? "help" : arguments[0].toLowerCase(Locale.ROOT);
        SimpleCommand child = children.get(childName);
        if (child == null) {
            Message.sendLang(invocation.source(), Lang.no_command);
            return;
        }
        child.execute(childInvocation(invocation, tail(arguments)));
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return true;
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] arguments = invocation.arguments();
        if (arguments.length <= 1) {
            String prefix = arguments.length == 0 ? "" : arguments[0].toLowerCase(Locale.ROOT);
            return CompletableFuture.completedFuture(children.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith(prefix))
                    .filter(entry -> entry.getValue().hasPermission(childInvocation(invocation, new String[0])))
                    .map(Map.Entry::getKey)
                    .sorted()
                    .toList());
        }
        SimpleCommand child = children.get(arguments[0].toLowerCase(Locale.ROOT));
        return child == null
                ? CompletableFuture.completedFuture(List.of())
                : child.suggestAsync(childInvocation(invocation, tail(arguments)));
    }

    private static Invocation childInvocation(Invocation parent, String[] arguments) {
        return new Invocation() {
            @Override public CommandSource source() { return parent.source(); }
            @Override public String alias() { return parent.alias(); }
            @Override public String[] arguments() { return arguments; }
        };
    }

    private static String[] tail(String[] arguments) {
        if (arguments.length <= 1) return new String[0];
        return java.util.Arrays.copyOfRange(arguments, 1, arguments.length);
    }
}
