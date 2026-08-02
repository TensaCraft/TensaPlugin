package ua.co.tensa.modules.authbridge;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import org.junit.jupiter.api.Test;
import ua.co.tensa.authbridge.protocol.AuthState;
import ua.co.tensa.authbridge.protocol.ProtocolConstants;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.co.tensa.modules.authbridge.AuthBridgeTestProxies.defaultValue;
import static ua.co.tensa.modules.authbridge.AuthBridgeTestProxies.proxy;

class AuthSessionRegistryTest {
    @Test
    void playerStartsPendingAndBackendChallengeOwnsSessionAndSequence() {
        Player player = player("Steve", UUID.randomUUID());
        ServerConnection connection = connection(player);
        UUID sessionId = UUID.randomUUID();
        byte[] challenge = bytes(ProtocolConstants.CHALLENGE_BYTES, 10);
        AuthSessionRegistry registry = new AuthSessionRegistry();

        assertThat(registry.begin(player)).isNull();
        assertThat(registry.ensure(player).state()).isEqualTo(AuthState.PENDING);
        assertThat(registry.update(player, AuthState.AWAITING_SECOND_FACTOR).state())
                .isEqualTo(AuthState.AWAITING_SECOND_FACTOR);

        AuthSessionRegistry.BindResult bound = registry.bind(
                player,
                connection,
                "aero",
                "aero-backend",
                sessionId,
                challenge
        );
        AuthSessionRegistry.Snapshot first = registry.next(player, connection);
        AuthSessionRegistry.Snapshot second = registry.next(player, connection);

        assertThat(bound.previousBinding()).isNull();
        assertThat(bound.snapshot().binding().sessionId()).isEqualTo(sessionId);
        assertThat(bound.snapshot().binding().challenge()).isEqualTo(challenge);
        assertThat(first.sequence()).isEqualTo(1L);
        assertThat(second.sequence()).isEqualTo(2L);
        assertThat(registry.markPublished(player, first)).isTrue();
        assertThat(registry.markPublished(player, second)).isFalse();
        assertThat(registry.nextIfStateChanged(player, connection)).isNull();

        registry.update(player, AuthState.AUTHORIZED);
        AuthSessionRegistry.Snapshot unsent = registry.nextIfStateChanged(player, connection);
        AuthSessionRegistry.Snapshot retry = registry.nextIfStateChanged(player, connection);

        assertThat(unsent.sequence()).isEqualTo(3L);
        assertThat(retry.sequence()).isEqualTo(4L);
        assertThat(registry.markPublished(player, retry)).isTrue();
        assertThat(registry.nextIfStateChanged(player, connection)).isNull();
    }

    @Test
    void replacementConnectionReturnsOldBindingAndRejectsStaleCallbacks() {
        UUID playerId = UUID.randomUUID();
        Player oldPlayer = player("Steve", playerId);
        Player newPlayer = player("Steve", playerId);
        ServerConnection oldConnection = connection(oldPlayer);
        AuthSessionRegistry registry = new AuthSessionRegistry();

        registry.begin(oldPlayer);
        registry.bind(
                oldPlayer,
                oldConnection,
                "aero",
                "aero-backend",
                UUID.randomUUID(),
                bytes(ProtocolConstants.CHALLENGE_BYTES, 20)
        );
        AuthSessionRegistry.Binding previous = registry.begin(newPlayer);

        assertThat(previous).isNotNull();
        assertThat(registry.update(oldPlayer, AuthState.AUTHORIZED)).isNull();
        assertThat(registry.remove(oldPlayer)).isNull();
        assertThat(registry.ensure(newPlayer).state()).isEqualTo(AuthState.PENDING);
        assertThat(registry.ensure(newPlayer).binding()).isNull();
    }

    @Test
    void staleBackendConnectionCannotAdvanceCurrentSequence() {
        Player player = player("Steve", UUID.randomUUID());
        ServerConnection oldConnection = connection(player);
        ServerConnection currentConnection = connection(player);
        AuthSessionRegistry registry = new AuthSessionRegistry();
        registry.begin(player);
        registry.bind(
                player,
                currentConnection,
                "aero",
                "aero-backend",
                UUID.randomUUID(),
                bytes(ProtocolConstants.CHALLENGE_BYTES, 30)
        );

        assertThat(registry.next(player, oldConnection)).isNull();
        assertThat(registry.next(player, currentConnection).sequence()).isEqualTo(1L);
        assertThat(registry.nextIfStateChanged(player, oldConnection)).isNull();
    }

    private Player player(String username, UUID uuid) {
        return proxy(Player.class, (method, args) -> switch (method.getName()) {
            case "getUsername" -> username;
            case "getUniqueId" -> uuid;
            default -> defaultValue(method.getReturnType());
        });
    }

    private ServerConnection connection(Player player) {
        return proxy(ServerConnection.class, (method, args) -> switch (method.getName()) {
            case "getPlayer" -> player;
            default -> defaultValue(method.getReturnType());
        });
    }

    private byte[] bytes(int length, int seed) {
        byte[] value = new byte[length];
        for (int index = 0; index < length; index++) {
            value[index] = (byte) (seed + index);
        }
        return value;
    }
}
