package ua.co.tensa.core.meta;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.co.tensa.Tensa;
import ua.co.tensa.core.user.UserDataService;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserMetaCommandPermissionTest {
    @TempDir Path tempDir;

    @Test
    void suggestionsDoNotRevealAnotherPlayersMetadataWithoutAdminPermission() throws Exception {
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Player source = player(sourceId, "Source", true);
        Player target = player(targetId, "Target", false);
        ProxyServer previous = Tensa.server;
        Tensa.server = (ProxyServer) Proxy.newProxyInstance(ProxyServer.class.getClassLoader(),
                new Class<?>[]{ProxyServer.class}, (proxy, method, args) ->
                        method.getName().equals("getPlayer") ? Optional.of(target) : null);
        try (UserDataService data = UserDataService.local(tempDir.resolve("users"), "tpl_");
             UserMetaService meta = new UserMetaService(data, true)) {
            meta.set(targetId, "private-key", "private-value", true);
            UserMetaCommand command = new UserMetaCommand(meta);

            assertThat(command.suggestAsync(invocation(source, "get", "Target", "")).join())
                    .isEmpty();
        } finally {
            Tensa.server = previous;
        }
    }

    private Player player(UUID uuid, String name, boolean basePermission) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getUsername" -> name;
                    case "hasPermission" -> basePermission && args[0].equals("tensa.meta");
                    default -> null;
                });
    }

    private SimpleCommand.Invocation invocation(Player source, String... args) {
        return (SimpleCommand.Invocation) Proxy.newProxyInstance(SimpleCommand.Invocation.class.getClassLoader(),
                new Class<?>[]{SimpleCommand.Invocation.class}, (proxy, method, ignored) -> switch (method.getName()) {
                    case "source" -> source;
                    case "arguments" -> args;
                    case "alias" -> "tmeta";
                    default -> null;
                });
    }
}
