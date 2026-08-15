package ua.co.tensa.commands;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TensaCommandTest {
    @Test
    void exposesOnlyTheUnifiedAdministrationSubcommands() {
        assertThat(TensaCommand.childNames())
                .containsExactlyInAnyOrder("help", "info", "modules", "reload")
                .doesNotContain("communications");
    }
}
