package ua.co.tensa.modules.rcon.manager;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RconPacketTest {
    @Test
    void parsesABoundedValidResponse() throws Exception {
        byte[] payload = "ok".getBytes(StandardCharsets.UTF_8);
        ByteBuffer frame = ByteBuffer.allocate(4 + 4 + 4 + payload.length + 2).order(ByteOrder.LITTLE_ENDIAN);
        frame.putInt(10 + payload.length).putInt(42).putInt(0).put(payload).put((byte) 0).put((byte) 0);

        RconPacket packet = RconPacket.readResponse(new ByteArrayInputStream(frame.array()));

        assertThat(packet.getRequestId()).isEqualTo(42);
        assertThat(packet.getPayload()).isEqualTo(payload);
    }

    @Test
    void rejectsOversizedFrameBeforeAllocatingItsBody() {
        ByteBuffer header = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(Integer.MAX_VALUE);

        assertThatThrownBy(() -> RconPacket.readResponse(new ByteArrayInputStream(header.array())))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("safe bounds");
    }

    @Test
    void rejectsMissingNullTerminators() {
        ByteBuffer frame = ByteBuffer.allocate(14).order(ByteOrder.LITTLE_ENDIAN);
        frame.putInt(10).putInt(1).putInt(0).put((byte) 1).put((byte) 0);

        assertThatThrownBy(() -> RconPacket.readResponse(new ByteArrayInputStream(frame.array())))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("terminators");
    }
}
