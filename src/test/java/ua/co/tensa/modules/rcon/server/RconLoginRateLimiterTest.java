package ua.co.tensa.modules.rcon.server;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class RconLoginRateLimiterTest {
    @Test
    void identityUsesIpRatherThanEphemeralPortAndBlocksAfterThreeFailures() {
        RconLoginRateLimiter limiter = new RconLoginRateLimiter(5_000, 300_000, 3, 64);
        String firstConnection = RconHandler.remoteAddressKey(new InetSocketAddress("192.0.2.10", 10001));
        String secondConnection = RconHandler.remoteAddressKey(new InetSocketAddress("192.0.2.10", 10002));

        assertThat(firstConnection).isEqualTo(secondConnection);
        limiter.failure(firstConnection, 1_000);
        limiter.failure(secondConnection, 7_000);
        limiter.failure(firstConnection, 13_000);

        assertThat(limiter.check(secondConnection, 14_000)).isEqualTo(RconLoginRateLimiter.Decision.BLOCKED);
    }

    @Test
    void attackerCannotGrowTrackingStateWithoutBound() {
        RconLoginRateLimiter limiter = new RconLoginRateLimiter(5_000, 300_000, 3, 32);
        for (int i = 0; i < 1_000; i++) {
            limiter.failure("192.0.2." + i, i);
        }
        assertThat(limiter.size()).isEqualTo(32);
    }
}
