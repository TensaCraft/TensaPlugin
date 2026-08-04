package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordRetryPolicyTest {
    @Test
    void appliesBoundedExponentialBackoffWithDeterministicJitter() {
        assertThat(DiscordRetryPolicy.delayMillis(1, ignored -> 0)).isEqualTo(250);
        assertThat(DiscordRetryPolicy.delayMillis(2, bound -> bound - 1)).isEqualTo(625);
        assertThat(DiscordRetryPolicy.delayMillis(3, ignored -> 0)).isEqualTo(1_000);
        assertThat(DiscordRetryPolicy.delayMillis(4, bound -> bound - 1)).isEqualTo(2_500);
        assertThat(DiscordRetryPolicy.delayMillis(8, ignored -> 0)).isEqualTo(2_000);
    }
}
