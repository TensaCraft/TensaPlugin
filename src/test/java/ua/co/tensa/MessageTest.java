package ua.co.tensa;

import org.junit.jupiter.api.Test;
import net.kyori.adventure.text.event.ClickEvent;

import static org.assertj.core.api.Assertions.assertThat;

class MessageTest {
    @Test
    void legacyNamedColorsMapToTheSharedDesignPalette() {
        assertThat(Message.applyDesignPalette("<red>Error</red> <gold>Title</gold> <gray>Text</gray>"))
                .isEqualTo("<#FF6B81>Error</#FF6B81> <#F4C15D>Title</#F4C15D> <#AAB4CC>Text</#AAB4CC>");
    }

    @Test
    void escapeMiniMessageEscapesTagsButKeepsLegacyAmpersands() {
        String escaped = Message.escapeMiniMessage("&4<response>");

        assertThat(escaped).contains("&4");
        assertThat(escaped).contains("\\<response>");
    }

    @Test
    void supportsCopyToClipboardActionsForInteractiveCodes() {
        var component = Message.convert("<click:copy_to_clipboard:'ABC123'>[Скопіювати]</click>");

        assertThat(component.clickEvent()).isNotNull();
        assertThat(component.clickEvent().action()).isEqualTo(ClickEvent.Action.COPY_TO_CLIPBOARD);
    }
}
