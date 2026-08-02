package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordEventFormatterTest {
    @TempDir
    Path tempDir;

    @Test
    void formatsUkrainianPlayerFacingMessagesUsingLabels() {
        DiscordSettings settings = DiscordTestSettings.create(tempDir, config -> config.serverLabels = Map.of(
                "aero", "Аеронавтика",
                "lobby", "Лобі"
        ));
        DiscordEventFormatter formatter = new DiscordEventFormatter(settings, new DiscordServerPolicy(settings));

        assertThat(formatter.join("Pilot", "aero"))
                .isEqualTo("🟢 Pilot приєднався до «Аеронавтика».");
        assertThat(formatter.quit("Pilot", "aero"))
                .isEqualTo("⚪ Pilot вийшов із «Аеронавтика».");
        assertThat(formatter.switchServer("Pilot", "lobby", "aero"))
                .isEqualTo("🔄 Pilot: «Лобі» → «Аеронавтика».");
        assertThat(formatter.backendUnavailable("aero"))
                .isEqualTo("🔴 «Аеронавтика» тимчасово недоступний.");
        assertThat(formatter.backendRecovered("aero"))
                .isEqualTo("🟢 «Аеронавтика» знову доступний.");
        assertThat(formatter.advancement("Pilot", "aero", "Кам'яна доба"))
                .isEqualTo("🏆 Pilot отримав досягнення «Кам'яна доба» на «Аеронавтика».");
    }

    @Test
    void neutralizesMentionsAndMarkdownInBackendValues() {
        DiscordSettings settings = DiscordTestSettings.create(tempDir);
        DiscordEventFormatter formatter = new DiscordEventFormatter(settings, new DiscordServerPolicy(settings));

        assertThat(formatter.advancement("@everyone", "aero", "**Небезпечно**"))
                .doesNotContain("@everyone", "**")
                .contains("@\u200Beveryone", "\\*\\*Небезпечно\\*\\*");
    }
}
