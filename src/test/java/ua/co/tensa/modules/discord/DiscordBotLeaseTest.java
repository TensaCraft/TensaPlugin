package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscordBotLeaseTest {
    @Test
    void onlyOneRuntimeCanOwnTheSameBotTokenInsideThePluginProcess() {
        Object firstOwner = new Object();
        Object secondOwner = new Object();
        DiscordBotLease first = DiscordBotLease.acquire("test-token-one", firstOwner);

        assertThatThrownBy(() -> DiscordBotLease.acquire("test-token-one", secondOwner))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("A Discord runtime for this bot is already active in this plugin process");

        first.close();
        assertThatCode(() -> DiscordBotLease.acquire("test-token-one", secondOwner).close())
                .doesNotThrowAnyException();
        assertThatCode(first::close).doesNotThrowAnyException();
    }
}
