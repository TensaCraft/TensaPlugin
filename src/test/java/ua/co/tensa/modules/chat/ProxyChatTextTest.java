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

    @Test
    void longConsoleSchedulerPayloadIsNotCut() {
        // Regression from the Aug 15 fix (c1d849e, never pushed): console/scheduler broadcasts such as this
        // voice-chat hint are far longer than a player chat line and must reach players complete.
        String payload = "<gradient:#55FFFF:#C792EA>✦ Голосовий чат</gradient> "
                + "<#667085>•</#667085> <#AAB4CC>меню відкривається клавішею</#AAB4CC> "
                + "<#F4C15D>V</#F4C15D><#AAB4CC>. Команди:</#AAB4CC> "
                + "<click:suggest_command:'/voicechat invite '><#F4C15D>/voicechat invite</#F4C15D></click>, "
                + "<click:suggest_command:'/voicechat join '><#F4C15D>/voicechat join</#F4C15D></click>, "
                + "<click:suggest_command:'/voicechat leave'><#F4C15D>/voicechat leave</#F4C15D></click>"
                + "<#AAB4CC>.</#AAB4CC>";

        assertThat(payload.length()).isGreaterThan(256);
        assertThat(ProxyChatText.normalize(payload)).isEqualTo(payload).endsWith("</#AAB4CC>");
    }
}
