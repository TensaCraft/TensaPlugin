package ua.co.tensa.modules.chat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyChatTextTest {

    @Test
    void stripsControlCharactersAndLimitsRepeatedCharacters() {
        assertThat(ProxyChatText.sanitize("  he\u0000llooooooo!!!  ", 256, 4))
                .isEqualTo("helloooo!!!");
    }

    @Test
    void appliesAConservativeLengthLimit() {
        assertThat(ProxyChatText.sanitize("123456789", 5, 4))
                .isEqualTo("12345");
    }

    @Test
    void normalizesDuplicateKeysWithoutChangingVisibleMessages() {
        assertThat(ProxyChatText.duplicateKey("  Привіт   Світе "))
                .isEqualTo("привіт світе");
    }

    @Test
    void rejectsBlankInput() {
        assertThat(ProxyChatText.sanitize(" \n\t ", 256, 4)).isEmpty();
    }
}
