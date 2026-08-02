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
