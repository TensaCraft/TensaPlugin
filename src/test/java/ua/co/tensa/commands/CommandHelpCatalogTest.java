package ua.co.tensa.commands;

import org.junit.jupiter.api.Test;
import ua.co.tensa.Message;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;

class CommandHelpCatalogTest {

    @Test
    void renderUsageEscapesMiniMessageLikeArgumentPlaceholders() {
        String usage = CommandHelpCatalog.renderUsage(
                "/{command} <player|uuid> <command...> [-t:seconds]",
                Map.of("command", "tqueue")
        );

        assertThat(usage)
                .contains("\\<player|uuid>")
                .contains("\\<command...>");
        assertThatCode(() -> Message.convert("<gold>" + usage + "</gold>"))
                .doesNotThrowAnyException();
    }
}
