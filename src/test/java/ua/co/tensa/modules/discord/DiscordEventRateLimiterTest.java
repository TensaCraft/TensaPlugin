package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordEventRateLimiterTest {
    @Test
    void rejectsBurstAboveLimitAndRecoversAfterWindow() {
        DiscordEventRateLimiter limiter = new DiscordEventRateLimiter(2);
        Instant now = Instant.parse("2026-08-02T09:00:00Z");

        assertThat(limiter.tryAcquire(now)).isTrue();
        assertThat(limiter.tryAcquire(now.plusSeconds(1))).isTrue();
        assertThat(limiter.tryAcquire(now.plusSeconds(2))).isFalse();
        assertThat(limiter.tryAcquire(now.plusSeconds(61))).isTrue();
    }
}
