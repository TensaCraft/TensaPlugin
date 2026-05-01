package ua.co.tensa.config.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.co.tensa.Tensa;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LangYAMLTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void resetSingleton() throws Exception {
        Tensa.pluginPath = tempDir;
        Tensa.config = null;
        Field instance = LangYAML.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, null);
    }

    @Test
    void generatedLanguageIncludesMetaDefaultsAndSafeMiniMessageUsageText() {
        LangYAML lang = LangYAML.getInstance();

        assertThat(lang.getString("meta_usage", ""))
                .contains("[set|get|del|list]")
                .doesNotContain("<set|get|del|list>");
        assertThat(lang.getString("queue_usage", ""))
                .contains("[player|uuid] [command...]")
                .doesNotContain("<player|uuid>", "<command...>");
    }

    @Test
    void syncAllLanguageFilesPreservesExistingComments() throws Exception {
        LangYAML template = LangYAML.getInstance();
        Path customLang = tempDir.resolve("lang").resolve("custom.yml");
        Files.writeString(customLang, """
                # Existing translator note.
                prefix: "<gray>[Custom]</gray> "
                """, StandardCharsets.UTF_8);

        LangYAML.syncAllLanguageFiles(template.getConfig());

        String saved = Files.readString(customLang, StandardCharsets.UTF_8);
        assertThat(saved).contains("# Existing translator note.");
        assertThat(saved).contains("queue_usage:");
    }
}
