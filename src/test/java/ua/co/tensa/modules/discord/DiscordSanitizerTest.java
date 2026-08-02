package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordSanitizerTest {
    @Test
    void minecraftTextRemovesControlsAndNeutralizesFormattingSyntax() {
        String sanitized = DiscordSanitizer.forMinecraft(
                "  Привіт\u0000\n<bold>x</bold> &a §c 😀  ",
                80
        );

        assertThat(sanitized)
                .isEqualTo("Привіт ＜bold＞x＜/bold＞ ＆a ＆c 😀")
                .doesNotContain("\u0000", "<bold>", "&a", "§c");
    }

    @Test
    void discordTextEscapesMarkdownMentionsAndTruncatesByCodePoint() {
        String sanitized = DiscordSanitizer.forDiscord("@everyone **тест** 😀😀", 20);

        assertThat(sanitized)
                .doesNotContain("@everyone", "**тест**")
                .contains("@\u200Beveryone", "\\*\\*тест\\*\\*");
        assertThat(sanitized.codePointCount(0, sanitized.length())).isGreaterThan(20);
        assertThat(DiscordSanitizer.truncate("😀😀😀", 2)).isEqualTo("😀😀");
    }
}
