package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LinkCodeRegistryTest {
    private static final UUID PLAYER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Test
    void codeIsConsumedExactlyOnce() {
        LinkCodeRegistry registry = new LinkCodeRegistry(Duration.ofMinutes(10), 6, () -> "ABCDEF");
        Instant now = Instant.parse("2026-08-02T09:00:00Z");

        assertThat(registry.issue(PLAYER, "Pilot", now)).isEqualTo("ABCDEF");
        LinkCodeRegistry.ConsumeResult first = registry.consume("abcdef", now.plusSeconds(1));
        LinkCodeRegistry.ConsumeResult second = registry.consume("ABCDEF", now.plusSeconds(2));

        assertThat(first.status()).isEqualTo(LinkCodeRegistry.ConsumeStatus.VALID);
        assertThat(first.pendingLink().playerUuid()).isEqualTo(PLAYER);
        assertThat(second.status()).isEqualTo(LinkCodeRegistry.ConsumeStatus.INVALID);
    }

    @Test
    void expiredCodeCannotBeUsedAndNewIssueInvalidatesPreviousCode() {
        AtomicInteger sequence = new AtomicInteger();
        LinkCodeRegistry registry = new LinkCodeRegistry(
                Duration.ofSeconds(30),
                6,
                () -> sequence.getAndIncrement() == 0 ? "AAAAAA" : "BBBBBB"
        );
        Instant now = Instant.parse("2026-08-02T09:00:00Z");

        registry.issue(PLAYER, "Pilot", now);
        registry.issue(PLAYER, "Pilot", now.plusSeconds(1));

        assertThat(registry.consume("AAAAAA", now.plusSeconds(2)).status())
                .isEqualTo(LinkCodeRegistry.ConsumeStatus.INVALID);
        assertThat(registry.consume("BBBBBB", now.plusSeconds(31)).status())
                .isEqualTo(LinkCodeRegistry.ConsumeStatus.EXPIRED);
    }

    @Test
    void periodicCleanupRemovesExpiredCodesAndCapacityIsBounded() {
        AtomicInteger sequence = new AtomicInteger();
        LinkCodeRegistry registry = new LinkCodeRegistry(
                Duration.ofSeconds(10), 6,
                () -> String.format("A%05d", sequence.incrementAndGet()),
                2
        );
        Instant now = Instant.parse("2026-08-02T09:00:00Z");
        registry.issue(PLAYER, "Pilot", now);
        registry.issue(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), "Copilot", now);

        assertThat(registry.size()).isEqualTo(2);
        assertThatThrownBy(() -> registry.issue(
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"), "Third", now))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("capacity");

        assertThat(registry.purgeExpired(now.plusSeconds(11))).isEqualTo(2);
        assertThat(registry.size()).isZero();
    }
}
