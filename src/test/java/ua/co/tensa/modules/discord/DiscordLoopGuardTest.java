package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordLoopGuardTest {
    private static final String GUILD = "12345678901234567";
    private static final String CHANNEL = "22345678901234567";
    private static final String SELF = "32345678901234567";

    @Test
    void acceptsOnlyHumanMessagesFromConfiguredGuildAndChannel() {
        assertThat(allowed(message("user", false, false, "hello"))).isTrue();
        assertThat(allowed(message("user", true, false, "hello"))).isFalse();
        assertThat(allowed(message("user", false, true, "hello"))).isFalse();
        assertThat(allowed(message(SELF, false, false, "hello"))).isFalse();
        assertThat(allowed(new DiscordInboundMessage(GUILD, "other", "user", "User", "hello", false, false))).isFalse();
        assertThat(allowed(new DiscordInboundMessage("other", CHANNEL, "user", "User", "hello", false, false))).isFalse();
        assertThat(allowed(message("user", false, false, "  "))).isFalse();
    }

    private static DiscordInboundMessage message(String authorId, boolean bot, boolean webhook, String content) {
        return new DiscordInboundMessage(GUILD, CHANNEL, authorId, "User", content, bot, webhook);
    }

    private static boolean allowed(DiscordInboundMessage message) {
        return DiscordLoopGuard.shouldRelay(message, GUILD, CHANNEL, SELF);
    }
}
