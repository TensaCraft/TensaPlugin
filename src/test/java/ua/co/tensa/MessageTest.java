package ua.co.tensa;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageTest {

    @Test
    void escapeMiniMessageEscapesTagsButKeepsLegacyAmpersands() {
        String escaped = Message.escapeMiniMessage("&4<response>");

        assertThat(escaped).contains("&4");
        assertThat(escaped).contains("\\<response>");
    }
}
