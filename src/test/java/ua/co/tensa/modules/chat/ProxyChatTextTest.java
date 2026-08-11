package ua.co.tensa.modules.chat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyChatTextTest {

    @Test
    void stripsControlCharactersWithoutModeratingRepeatedCharacters() {
        assertThat(ProxyChatText.normalize("  he\u0000llooooooo!!!  "))
                .isEqualTo("hellooooooo!!!");
    }

    @Test
    void doesNotSilentlyTruncateChatMessages() {
        assertThat(ProxyChatText.normalize("123456789"))
                .isEqualTo("123456789");
    }

    @Test
    void rejectsBlankInput() {
        assertThat(ProxyChatText.normalize(" \n\t ")).isEmpty();
    }
}
