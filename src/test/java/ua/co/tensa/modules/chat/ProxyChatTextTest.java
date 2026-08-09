package ua.co.tensa.modules.chat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyChatTextTest {

    @Test
    void stripsControlCharactersWithoutModeratingRepeatedCharacters() {
        assertThat(ProxyChatText.sanitize("  he\u0000llooooooo!!!  ", 256))
                .isEqualTo("hellooooooo!!!");
    }

    @Test
    void appliesAConservativeLengthLimit() {
        assertThat(ProxyChatText.sanitize("123456789", 5))
                .isEqualTo("12345");
    }

    @Test
    void rejectsBlankInput() {
        assertThat(ProxyChatText.sanitize(" \n\t ", 256)).isEmpty();
    }
}
