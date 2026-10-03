package ua.co.tensa.modules.bridge;

import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.ServerInfo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegacyProxyBridgeRuntimeTest {
    private static final String CHANNEL = "tensa:exec";
    private static final String TOKEN = "dedicated-bridge-token";

    @Test
    void handlesMatchingChannelImmediatelyAndExecutesOnlyAllowedServerCommands() {
        List<String> commands = new ArrayList<>();
        LegacyProxyBridgeRuntime runtime = runtime(List.of("aero"), commands);
        Player player = proxy(
                Player.class,
                (ignored, method, args) -> defaultValue(method.getReturnType())
        );
        ServerConnection connection = connection("AERO", player);
        PluginMessageEvent event = event(
                connection,
                player,
                CHANNEL,
                TOKEN + ":say bridge-ok"
        );

        runtime.onPluginMessage(event);

        assertThat(event.getResult().isAllowed()).isFalse();
        assertThat(commands).containsExactly("say bridge-ok");
        runtime.close();
    }

    @Test
    void consumesMatchingChannelBeforeRejectingPlayerSourceDisallowedServerOrToken() {
        List<String> commands = new ArrayList<>();
        LegacyProxyBridgeRuntime runtime = runtime(List.of("aero"), commands);
        Player player = proxy(
                Player.class,
                (ignored, method, args) -> defaultValue(method.getReturnType())
        );

        PluginMessageEvent playerSource = event(
                player,
                connection("aero", player),
                CHANNEL,
                TOKEN + ":say denied"
        );
        runtime.onPluginMessage(playerSource);

        PluginMessageEvent disallowed = event(
                connection("other", player),
                player,
                CHANNEL,
                TOKEN + ":say denied"
        );
        runtime.onPluginMessage(disallowed);

        PluginMessageEvent wrongToken = event(
                connection("aero", player),
                player,
                CHANNEL,
                "wrong:say denied"
        );
        runtime.onPluginMessage(wrongToken);

        assertThat(playerSource.getResult().isAllowed()).isFalse();
        assertThat(disallowed.getResult().isAllowed()).isFalse();
        assertThat(wrongToken.getResult().isAllowed()).isFalse();
        assertThat(commands).isEmpty();
        runtime.close();
    }

    @Test
    void ignoresOtherChannelsWithoutConsumingThem() {
        List<String> commands = new ArrayList<>();
        LegacyProxyBridgeRuntime runtime = runtime(List.of("aero"), commands);
        Player player = proxy(
                Player.class,
                (ignored, method, args) -> defaultValue(method.getReturnType())
        );
        PluginMessageEvent event = event(
                connection("aero", player),
                player,
                "other:channel",
                TOKEN + ":say ignored"
        );

        runtime.onPluginMessage(event);

        assertThat(event.getResult().isAllowed()).isTrue();
        assertThat(commands).isEmpty();
        runtime.close();
    }

    @Test
    void closedRuntimeDoesNotAuthenticateAnErasedToken() {
        List<String> commands = new ArrayList<>();
        LegacyProxyBridgeRuntime runtime = runtime(List.of("aero"), commands);
        Player player = proxy(Player.class,
                (ignored, method, args) -> defaultValue(method.getReturnType()));
        runtime.close();

        PluginMessageEvent event = event(connection("aero", player), player, CHANNEL,
                "\0".repeat(TOKEN.length()) + ":say denied");
        runtime.onPluginMessage(event);

        assertThat(event.getResult().isAllowed()).isFalse();
        assertThat(commands).isEmpty();
    }

    @Test
    void rejectsUnsafeOrImplicitCompatibilityConfiguration() {
        assertThatThrownBy(() -> new LegacyProxyBridgeRuntime(
                false,
                CHANNEL,
                TOKEN,
                List.of("aero"),
                false,
                ignored -> {
                }
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("compatibility_mode");

        assertThatThrownBy(() -> runtime(List.of(), new ArrayList<>()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("allow_from");
        assertThatThrownBy(() -> runtime(List.of("*"), new ArrayList<>()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wildcard");
        assertThatThrownBy(() -> new LegacyProxyBridgeRuntime(
                true,
                CHANNEL,
                " ",
                List.of("aero"),
                false,
                ignored -> {
                }
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dedicated");
    }

    private LegacyProxyBridgeRuntime runtime(List<String> allowFrom, List<String> commands) {
        return new LegacyProxyBridgeRuntime(
                true,
                CHANNEL,
                TOKEN,
                allowFrom,
                false,
                commands::add
        );
    }

    private PluginMessageEvent event(
            Object source,
            Object target,
            String channel,
            String payload
    ) {
        return new PluginMessageEvent(
                (com.velocitypowered.api.proxy.messages.ChannelMessageSource) source,
                (com.velocitypowered.api.proxy.messages.ChannelMessageSink) target,
                MinecraftChannelIdentifier.from(channel),
                payload.getBytes(StandardCharsets.UTF_8)
        );
    }

    private ServerConnection connection(String name, Player player) {
        ServerInfo info = new ServerInfo(name, new InetSocketAddress("127.0.0.1", 25565));
        return proxy(ServerConnection.class, (ignored, method, args) -> switch (method.getName()) {
            case "getServerInfo" -> info;
            case "getPlayer" -> player;
            default -> defaultValue(method.getReturnType());
        });
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }
}
