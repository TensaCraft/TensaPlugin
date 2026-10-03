package ua.co.tensa.modules.chat;

import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.co.tensa.Tensa;
import ua.co.tensa.modules.chat.data.ChatConfig;
import ua.co.tensa.modules.discord.data.DiscordConfig;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyChatListenerTest {
    @TempDir
    Path tempDir;
    private final ProxyServer previousServer = Tensa.server;
    private final Path previousPath = Tensa.pluginPath;
    private final List<ProxyChatMessage> relayed = new ArrayList<>();

    @AfterEach
    void restoreGlobals() {
        Tensa.server = previousServer;
        Tensa.pluginPath = previousPath;
    }

    @Test
    @SuppressWarnings("deprecation") // Simulate a result already modified by another plugin.
    void doesNotRelayChatAlreadyDeniedByAnotherPlugin() throws Exception {
        Fixture fixture = fixture("");
        PlayerChatEvent event = new PlayerChatEvent(fixture.player(), "blocked message");
        event.setResult(PlayerChatEvent.ChatResult.denied());

        fixture.listener().onPlayerChat(event);

        assertThat(relayed).isEmpty();
        assertThat(event.getResult().isAllowed()).isFalse();
    }

    @Test
    @SuppressWarnings("deprecation") // Simulate a result already modified by another plugin.
    void relaysTheFilteredResultInsteadOfTheOriginalChatText() throws Exception {
        Fixture fixture = fixture("");
        PlayerChatEvent event = new PlayerChatEvent(fixture.player(), "original unfiltered message");
        event.setResult(PlayerChatEvent.ChatResult.message("filtered message"));

        fixture.listener().onPlayerChat(event);

        assertThat(relayed).extracting(ProxyChatMessage::message).containsExactly("filtered message");
    }

    @Test
    void nativeChatRequiresTheSameSendPermissionAsTheChannelCommand() throws Exception {
        Fixture fixture = fixture("tensa.chat.global");
        PlayerChatEvent event = new PlayerChatEvent(fixture.player(), "message without permission");

        fixture.listener().onPlayerChat(event);

        assertThat(relayed).isEmpty();
        // Refusing the proxy relay must not cancel signed chat on modern clients.
        assertThat(event.getResult().isAllowed()).isTrue();
    }

    @Test
    void allowedNativeChatIsRelayedWithoutDenyingSignedChat() throws Exception {
        Fixture fixture = fixture("");
        PlayerChatEvent event = new PlayerChatEvent(fixture.player(), "allowed message");

        fixture.listener().onPlayerChat(event);

        assertThat(relayed).extracting(ProxyChatMessage::message).containsExactly("allowed message");
        assertThat(event.getResult().isAllowed()).isTrue();
    }

    private Fixture fixture(String permission) throws Exception {
        Tensa.pluginPath = tempDir;
        Files.createDirectories(tempDir.resolve("communications"));
        Files.writeString(tempDir.resolve("communications/chats.yml"), """
                config_version: 2
                global:
                  enabled: true
                  native: true
                  permission: '%s'
                  format: '{player}: {message}'
                """.formatted(permission));
        ChatConfig chats = new ChatConfig();
        chats.reloadCfg();
        DiscordConfig discord = new DiscordConfig();
        discord.reloadCfg();
        Player player = proxy(Player.class, (ignored, method, args) -> switch (method.getName()) {
            case "getCurrentServer" -> Optional.empty();
            case "getUniqueId" -> UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
            case "getUsername" -> "Pilot";
            case "hasPermission" -> false;
            default -> throw new AssertionError("Unexpected player method " + method.getName());
        });
        Tensa.server = proxy(ProxyServer.class, (ignored, method, args) -> switch (method.getName()) {
            case "getPlayer" -> Optional.empty();
            case "getAllPlayers" -> List.of();
            case "getConsoleCommandSource" -> null;
            default -> throw new AssertionError("Unexpected proxy method " + method.getName());
        });
        ProxyChatService service = new ProxyChatService(chats.adapter(), discord.adapter(), message -> {
            relayed.add(message);
            return ProxyChatRelay.Result.ACCEPTED;
        });
        return new Fixture(player, new ProxyChatListener(service, new ChatCommands(chats.adapter(), service)));
    }

    private record Fixture(Player player, ProxyChatListener listener) { }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
