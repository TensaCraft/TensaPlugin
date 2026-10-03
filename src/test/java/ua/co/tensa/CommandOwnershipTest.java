package ua.co.tensa;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommandOwnershipTest {
    @Test
    void refusesForeignAliasAndDoesNotRemoveACommandThatChangedOwners() {
        Map<String, CommandMeta> commands = new HashMap<>();
        CommandMeta foreign = proxy(CommandMeta.class, (instance, method, args) -> null);
        CommandMeta owned = proxy(CommandMeta.class, (instance, method, args) -> null);
        CommandMeta.Builder builder = proxy(CommandMeta.Builder.class,
                (instance, method, args) -> method.getName().equals("build") ? owned : instance);
        CommandManager manager = proxy(CommandManager.class, (instance, method, args) -> {
            return switch (method.getName()) {
                case "hasCommand" -> commands.containsKey(args[0]);
                case "getCommandMeta" -> commands.get(args[0]);
                case "metaBuilder" -> builder;
                case "register" -> { commands.put("auditcommand", (CommandMeta) args[0]); yield null; }
                case "unregister" -> { commands.remove(args[0]); yield null; }
                default -> null;
            };
        });
        ProxyServer previous = Tensa.server;
        try {
            Tensa.server = proxy(ProxyServer.class, (instance, method, args) ->
                    method.getName().equals("getCommandManager") ? manager : null);
            commands.put("foreignalias", foreign);
            SimpleCommand handler = invocation -> {};
            assertThatThrownBy(() -> Util.registerCommand("auditcommand", "foreignalias", handler))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(commands).doesNotContainKey("auditcommand");

            Util.registerCommand("auditcommand", "", handler);
            commands.put("auditcommand", foreign);
            Util.unregisterCommand("auditcommand");
            assertThat(commands.get("auditcommand")).isSameAs(foreign);
            assertThat(Util.getRegisteredCommands()).noneMatch(command -> command.primary().equals("auditcommand"));
        } finally {
            Util.unregisterCommand("auditcommand");
            Tensa.server = previous;
        }
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
