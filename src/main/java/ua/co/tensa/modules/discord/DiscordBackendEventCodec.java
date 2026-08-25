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

    private static final int MAGIC_V1 = 0x54444531;
    private static final int MAGIC_V2 = 0x54444532;
    private static final int ADVANCEMENT = 1;
    private static final int DEATH = 2;
    private static final int MAX_V1_PACKET_BYTES = 2_048;
    private static final int MAX_V2_PACKET_BYTES = 4_096;
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern ADVANCEMENT_KEY = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern LOCALE = Pattern.compile("[a-z]{2}_[a-z]{2}");

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
                output.writeInt(MAGIC_V1);
                output.writeByte(ADVANCEMENT);
                output.writeLong(playerUuid.getMostSignificantBits());
                output.writeLong(playerUuid.getLeastSignificantBits());
                writeString(output, playerName, 64);
                writeString(output, advancementKey, 256);
                writeString(output, cleanTitle(title), 1_024);
            }
            byte[] packet = bytes.toByteArray();
            if (packet.length > MAX_V1_PACKET_BYTES) {
                throw new IllegalArgumentException("Discord backend event exceeds the packet limit");
            }
            return packet;
        } catch (IOException e) {
            throw new IllegalStateException("Discord backend event could not be encoded", e);
        }
    }

    public static byte[] encodeAdvancementV2(
            UUID playerUuid,
            String playerName,
            String advancementKey,
            String locale,
            String title,
            String description
    ) {
        String cleanDescription = cleanDescription(description);
        validateAdvancementV2(
                playerUuid,
                playerName,
                advancementKey,
                locale,
                title,
                cleanDescription
        );
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(MAGIC_V2);
                output.writeByte(ADVANCEMENT);
                output.writeLong(playerUuid.getMostSignificantBits());
                output.writeLong(playerUuid.getLeastSignificantBits());
                writeString(output, playerName, 64);
                writeString(output, advancementKey, 256);
                writeString(output, locale, 16);
                writeString(output, cleanTitle(title), 1_024);
                output.writeBoolean(!cleanDescription.isBlank());
                if (!cleanDescription.isBlank()) {
                    writeString(output, cleanDescription, 2_048);
                }
            }
            byte[] packet = bytes.toByteArray();
            if (packet.length > MAX_V2_PACKET_BYTES) {
                throw new IllegalArgumentException("Discord backend event exceeds the packet limit");
            }
            return packet;
        } catch (IOException e) {
            throw new IllegalStateException("Discord backend event could not be encoded", e);
        }
    }

    public static byte[] encodeDeathV2(
            UUID playerUuid,
            String playerName,
            String locale,
            String message
    ) {
        String cleanMessage = cleanDescription(message);
        validateDeath(playerUuid, playerName, locale, cleanMessage);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(MAGIC_V2);
                output.writeByte(DEATH);
                output.writeLong(playerUuid.getMostSignificantBits());
                output.writeLong(playerUuid.getLeastSignificantBits());
                writeString(output, playerName, 64);
                writeString(output, locale, 16);
                writeString(output, cleanMessage, 2_048);
            }
            byte[] packet = bytes.toByteArray();
            if (packet.length > MAX_V2_PACKET_BYTES) {
                throw new IllegalArgumentException("Discord backend event exceeds the packet limit");
            }
            return packet;
        } catch (IOException e) {
            throw new IllegalStateException("Discord backend event could not be encoded", e);
        }
    }

    public static DiscordBackendEvent decode(byte[] packet) {
        if (packet == null || packet.length == 0 || packet.length > MAX_V2_PACKET_BYTES) {
            throw new IllegalArgumentException("Invalid Discord backend event packet size");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(packet))) {
            int magic = input.readInt();
            if (magic == MAGIC_V1 && packet.length > MAX_V1_PACKET_BYTES) {
                throw new IllegalArgumentException("Discord backend event exceeds the v1 packet limit");
            }
            if (magic != MAGIC_V1 && magic != MAGIC_V2) {
                throw new IllegalArgumentException("Unsupported Discord backend event packet");
            }
            int eventType = input.readUnsignedByte();
            if (magic == MAGIC_V1 && eventType != ADVANCEMENT) {
                throw new IllegalArgumentException("Unsupported Discord backend event packet");
            }
            UUID playerUuid = new UUID(input.readLong(), input.readLong());
            String playerName = readString(input, 64);
            if (eventType == DEATH && magic == MAGIC_V2) {
                String locale = readString(input, 16);
                String message = cleanDescription(readString(input, 2_048));
                if (input.available() != 0) {
                    throw new IllegalArgumentException("Discord backend event contains trailing data");
                }
                validateDeath(playerUuid, playerName, locale, message);
                return new DiscordBackendEvent.Death(playerUuid, playerName, locale, message);
            }
            if (eventType != ADVANCEMENT) {
                throw new IllegalArgumentException("Unsupported Discord backend event packet");
            }
            String advancementKey = readString(input, 256);
            String locale = magic == MAGIC_V2 ? readString(input, 16) : "";
            String title = cleanTitle(readString(input, 1_024));
            String description = "";
            if (magic == MAGIC_V2) {
                int descriptionFlag = input.readUnsignedByte();
                if (descriptionFlag > 1) {
                    throw new IllegalArgumentException("Invalid optional description flag");
                }
                if (descriptionFlag == 1) {
                    description = cleanDescription(readString(input, 2_048));
                }
            }
            if (input.available() != 0) {
                throw new IllegalArgumentException("Discord backend event contains trailing data");
            }
            if (magic == MAGIC_V1) {
                validateAdvancement(playerUuid, playerName, advancementKey, title);
            } else {
                validateAdvancementV2(
                        playerUuid,
                        playerName,
                        advancementKey,
                        locale,
                        title,
                        description
                );
            }
            return new DiscordBackendEvent.Advancement(
                    playerUuid,
                    playerName,
                    advancementKey,
                    locale,
                    title,
                    description
            );
        } catch (IOException e) {
            throw new IllegalArgumentException("Malformed Discord backend event packet", e);
        }
    }

    private static void validateAdvancementV2(
            UUID playerUuid,
            String playerName,
            String advancementKey,
            String locale,
            String title,
            String description
    ) {
        validateAdvancement(playerUuid, playerName, advancementKey, title);
        if (!LOCALE.matcher(locale == null ? "" : locale).matches()) {
            throw new IllegalArgumentException("Invalid advancement locale");
        }
        String cleanDescription = cleanDescription(description);
        if (cleanDescription.codePointCount(0, cleanDescription.length()) > 500) {
            throw new IllegalArgumentException("Invalid advancement description");
        }
    }

    private static void validateAdvancement(UUID playerUuid, String playerName, String advancementKey, String title) {
        validatePlayer(playerUuid, playerName);
        if (!ADVANCEMENT_KEY.matcher(advancementKey == null ? "" : advancementKey).matches()) {
            throw new IllegalArgumentException("Invalid advancement identity");
        }
        String cleanTitle = cleanTitle(title);
        if (cleanTitle.isBlank() || cleanTitle.codePointCount(0, cleanTitle.length()) > 200) {
            throw new IllegalArgumentException("Invalid advancement title");
        }
    }

    private static void validateDeath(UUID playerUuid, String playerName, String locale, String message) {
        validatePlayer(playerUuid, playerName);
        if (!LOCALE.matcher(locale == null ? "" : locale).matches()) {
            throw new IllegalArgumentException("Invalid death locale");
        }
        String cleanMessage = cleanDescription(message);
        if (cleanMessage.isBlank() || cleanMessage.codePointCount(0, cleanMessage.length()) > 1_000) {
            throw new IllegalArgumentException("Invalid death message");
        }
    }

    private static void validatePlayer(UUID playerUuid, String playerName) {
        if (playerUuid == null
                || !PLAYER_NAME.matcher(playerName == null ? "" : playerName).matches()) {
            throw new IllegalArgumentException("Invalid player identity");
        }
    }

    private static String cleanTitle(String title) {
        return DiscordSanitizer.normalize(title == null ? "" : title).trim();
    }

    private static String cleanDescription(String description) {
        return DiscordSanitizer.normalize(description == null ? "" : description).trim();
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
