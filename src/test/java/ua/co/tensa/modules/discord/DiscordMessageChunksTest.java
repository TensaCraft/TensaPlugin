package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordMessageChunksTest {
    @Test
    void preservesEveryCodePointAcrossDiscordSizedChunks() {
        String original = "слово ".repeat(700) + "😀".repeat(300);

        var chunks = DiscordMessageChunks.split(original, 2_000);

        assertThat(chunks).hasSizeGreaterThan(1).allSatisfy(chunk ->
                assertThat(chunk.codePointCount(0, chunk.length())).isLessThanOrEqualTo(2_000));
        assertThat(String.join("", chunks)).isEqualTo(original);
    }

    @Test
    void keepsDiscordEscapePrefixWithItsEscapedCharacter() {
        String original = "x".repeat(1_999) + "\\*tail";

        var chunks = DiscordMessageChunks.split(original, 2_000);

        assertThat(chunks).containsExactly("x".repeat(1_999), "\\*tail");
    }
}
