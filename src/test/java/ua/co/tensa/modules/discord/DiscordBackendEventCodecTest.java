package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscordBackendEventCodecTest {
    private static final UUID PLAYER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Test
    void roundTripsAdvancementAndCleansControlCharacters() {
        byte[] packet = DiscordBackendEventCodec.encodeAdvancement(
                PLAYER,
                "Pilot",
                "minecraft:story/mine_stone",
                "Кам'яна\u0000 доба"
        );

        assertThat(DiscordBackendEventCodec.decode(packet)).isEqualTo(new DiscordBackendEvent.Advancement(
                PLAYER,
                "Pilot",
                "minecraft:story/mine_stone",
                "Кам'яна доба"
        ));
    }

    @Test
    void rejectsTrailingDataInvalidIdentityAndOversizedFields() {
        byte[] valid = DiscordBackendEventCodec.encodeAdvancement(
                PLAYER, "Pilot", "minecraft:story/mine_stone", "Кам'яна доба");

        assertThatThrownBy(() -> DiscordBackendEventCodec.decode(Arrays.copyOf(valid, valid.length + 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DiscordBackendEventCodec.encodeAdvancement(
                PLAYER, "bad player", "minecraft:story/mine_stone", "Кам'яна доба"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DiscordBackendEventCodec.encodeAdvancement(
                PLAYER, "Pilot", "minecraft:story/mine_stone", "x".repeat(1_100)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void roundTripsTde2WithAndWithoutAnOptionalDescription() {
        byte[] described = DiscordBackendEventCodec.encodeAdvancementV2(
                PLAYER,
                "Pilot",
                "minecraft:adventure/kill_a_mob",
                "uk_ua",
                "Мисливець на монстрів",
                "Убийте будь-яку ворожу істоту"
        );
        byte[] titleOnly = DiscordBackendEventCodec.encodeAdvancementV2(
                PLAYER,
                "Pilot",
                "examplemod:story/start",
                "en_us",
                "A New Beginning",
                null
        );

        assertThat(DiscordBackendEventCodec.decode(described)).isEqualTo(
                new DiscordBackendEvent.Advancement(
                        PLAYER,
                        "Pilot",
                        "minecraft:adventure/kill_a_mob",
                        "uk_ua",
                        "Мисливець на монстрів",
                        "Убийте будь-яку ворожу істоту"
                )
        );
        assertThat(DiscordBackendEventCodec.decode(titleOnly)).isEqualTo(
                new DiscordBackendEvent.Advancement(
                        PLAYER,
                        "Pilot",
                        "examplemod:story/start",
                        "en_us",
                        "A New Beginning",
                        ""
                )
        );
    }

    @Test
    void rejectsMalformedTruncatedOversizedAndInvalidUtf8Tde2Packets() {
        byte[] valid = DiscordBackendEventCodec.encodeAdvancementV2(
                PLAYER,
                "Pilot",
                "minecraft:story/mine_stone",
                "uk_ua",
                "X",
                null
        );
        byte[] invalidUtf8 = valid.clone();
        invalidUtf8[invalidUtf8.length - 2] = (byte) 0xC3;

        assertThatThrownBy(() -> DiscordBackendEventCodec.decode(
                Arrays.copyOf(valid, valid.length - 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DiscordBackendEventCodec.decode(
                Arrays.copyOf(valid, valid.length + 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DiscordBackendEventCodec.decode(new byte[4_097]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DiscordBackendEventCodec.decode(invalidUtf8))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DiscordBackendEventCodec.encodeAdvancementV2(
                PLAYER,
                "Pilot",
                "minecraft:story/mine_stone",
                "uk-UA",
                "Stone Age",
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void roundTripsLocalizedDeathAndCleansControlCharacters() {
        byte[] packet = DiscordBackendEventCodec.encodeDeathV2(
                PLAYER,
                "Pilot",
                "uk_ua",
                "Pilot\u0000 був підірваний Кріпером"
        );

        assertThat(DiscordBackendEventCodec.decode(packet)).isEqualTo(
                new DiscordBackendEvent.Death(
                        PLAYER,
                        "Pilot",
                        "uk_ua",
                        "Pilot був підірваний Кріпером"
                )
        );
    }

    @Test
    void rejectsInvalidOrMalformedDeathPackets() {
        byte[] valid = DiscordBackendEventCodec.encodeDeathV2(
                PLAYER, "Pilot", "en_us", "Pilot fell from a high place");

        assertThatThrownBy(() -> DiscordBackendEventCodec.decode(Arrays.copyOf(valid, valid.length - 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DiscordBackendEventCodec.decode(Arrays.copyOf(valid, valid.length + 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DiscordBackendEventCodec.encodeDeathV2(
                PLAYER, "Pilot", "en-US", "Pilot died"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DiscordBackendEventCodec.encodeDeathV2(
                PLAYER, "Pilot", "en_us", ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DiscordBackendEventCodec.encodeDeathV2(
                PLAYER, "Pilot", "en_us", "x".repeat(2_100)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void advancementDedupIsBoundedAndExpires() {
        RecentEventDeduplicator dedup = new RecentEventDeduplicator(Duration.ofSeconds(10), 2);
        Instant now = Instant.parse("2026-08-02T09:00:00Z");

        assertThat(dedup.firstOccurrence("a", now)).isTrue();
        assertThat(dedup.firstOccurrence("a", now.plusSeconds(1))).isFalse();
        assertThat(dedup.firstOccurrence("a", now.plusSeconds(11))).isTrue();
        dedup.firstOccurrence("b", now.plusSeconds(11));
        dedup.firstOccurrence("c", now.plusSeconds(11));
        assertThat(dedup.size()).isEqualTo(2);
    }
}
