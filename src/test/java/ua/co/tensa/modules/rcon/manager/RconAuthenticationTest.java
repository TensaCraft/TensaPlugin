package ua.co.tensa.modules.rcon.manager;

import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RconAuthenticationTest {
    @Test
    void consumesAuthenticationResponseBeforeReadingCommandResult() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            CompletableFuture<Void> served = serve(server, false);
            try (Rcon client = new Rcon("127.0.0.1", server.getLocalPort(), new byte[]{1})) {
                assertThat(client.command("status")).isEqualTo("actual status");
            }
            served.get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void doesNotTreatPrecedingResponseValueAsSuccessfulAuthentication() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            CompletableFuture<Void> served = serve(server, true);
            assertThatThrownBy(() -> {
                try (Rcon ignored = new Rcon("127.0.0.1", server.getLocalPort(), new byte[]{1})) {
                    // A RESPONSE_VALUE alone must not authenticate the client.
                }
            }).isInstanceOf(AuthenticationException.class);
            served.get(2, TimeUnit.SECONDS);
        }
    }

    private CompletableFuture<Void> serve(ServerSocket server, boolean reject) {
        return CompletableFuture.runAsync(() -> {
            try (var socket = server.accept()) {
                socket.setSoTimeout(2000);
                DataInputStream input = new DataInputStream(socket.getInputStream());
                int requestId = readId(input);
                writePacket(socket.getOutputStream(), requestId, 0, "");
                writePacket(socket.getOutputStream(), reject ? -1 : requestId, 2, "");
                if (!reject) {
                    readId(input);
                    writePacket(socket.getOutputStream(), requestId, 0, "actual status");
                }
            } catch (Exception failure) {
                throw new java.util.concurrent.CompletionException(failure);
            }
        });
    }

    private int readId(DataInputStream input) throws Exception {
        int size = Integer.reverseBytes(input.readInt());
        return ByteBuffer.wrap(input.readNBytes(size)).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private void writePacket(OutputStream output, int id, int type, String message) throws Exception {
        byte[] payload = message.getBytes(StandardCharsets.UTF_8);
        ByteBuffer packet = ByteBuffer.allocate(payload.length + 14).order(ByteOrder.LITTLE_ENDIAN);
        packet.putInt(payload.length + 10).putInt(id).putInt(type).put(payload).put((byte) 0).put((byte) 0);
        output.write(packet.array());
        output.flush();
    }
}
