package ua.co.tensa.modules.discord;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;

public final class DiscordBackendEventCodec {
    public static final String DEFAULT_CHANNEL = "tensa:discord_events";

    private static final int MAGIC = 0x54444531;
    private static final int ADVANCEMENT = 1;
    private static final int MAX_PACKET_BYTES = 2_048;
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern ADVANCEMENT_KEY = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private DiscordBackendEventCodec() {
    }

    public static byte[] encodeAdvancement(
            UUID playerUuid,
            String playerName,
            String advancementKey,
            String title
    ) {
        validateAdvancement(playerUuid, playerName, advancementKey, title);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(MAGIC);
                output.writeByte(ADVANCEMENT);
                output.writeLong(playerUuid.getMostSignificantBits());
                output.writeLong(playerUuid.getLeastSignificantBits());
                writeString(output, playerName, 64);
                writeString(output, advancementKey, 256);
                writeString(output, cleanTitle(title), 1_024);
            }
            byte[] packet = bytes.toByteArray();
            if (packet.length > MAX_PACKET_BYTES) {
                throw new IllegalArgumentException("Discord backend event exceeds the packet limit");
            }
            return packet;
        } catch (IOException e) {
            throw new IllegalStateException("Discord backend event could not be encoded", e);
        }
    }

    public static DiscordBackendEvent decode(byte[] packet) {
        if (packet == null || packet.length == 0 || packet.length > MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("Invalid Discord backend event packet size");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(packet))) {
            if (input.readInt() != MAGIC || input.readUnsignedByte() != ADVANCEMENT) {
                throw new IllegalArgumentException("Unsupported Discord backend event packet");
            }
            UUID playerUuid = new UUID(input.readLong(), input.readLong());
            String playerName = readString(input, 64);
            String advancementKey = readString(input, 256);
            String title = cleanTitle(readString(input, 1_024));
            if (input.available() != 0) {
                throw new IllegalArgumentException("Discord backend event contains trailing data");
            }
            validateAdvancement(playerUuid, playerName, advancementKey, title);
            return new DiscordBackendEvent.Advancement(playerUuid, playerName, advancementKey, title);
        } catch (IOException e) {
            throw new IllegalArgumentException("Malformed Discord backend event packet", e);
        }
    }

    private static void validateAdvancement(UUID playerUuid, String playerName, String advancementKey, String title) {
        if (playerUuid == null
                || !PLAYER_NAME.matcher(playerName == null ? "" : playerName).matches()
                || !ADVANCEMENT_KEY.matcher(advancementKey == null ? "" : advancementKey).matches()) {
            throw new IllegalArgumentException("Invalid advancement identity");
        }
        String cleanTitle = cleanTitle(title);
        if (cleanTitle.isBlank() || cleanTitle.codePointCount(0, cleanTitle.length()) > 200) {
            throw new IllegalArgumentException("Invalid advancement title");
        }
    }

    private static String cleanTitle(String title) {
        return DiscordSanitizer.normalize(title == null ? "" : title).trim();
    }

    private static void writeString(DataOutputStream output, String value, int maxBytes) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > maxBytes) {
            throw new IllegalArgumentException("Discord backend event text exceeds its field limit");
        }
        output.writeShort(encoded.length);
        output.write(encoded);
    }

    private static String readString(DataInputStream input, int maxBytes) throws IOException {
        int length = input.readUnsignedShort();
        if (length > maxBytes) {
            throw new IllegalArgumentException("Discord backend event text exceeds its field limit");
        }
        byte[] encoded = input.readNBytes(length);
        if (encoded.length != length) {
            throw new IllegalArgumentException("Truncated Discord backend event text");
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(encoded))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("Discord backend event text is not valid UTF-8", e);
        }
    }
}
