package ua.co.tensa.config.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.co.tensa.Tensa;
import ua.co.tensa.config.Config;
import ua.co.tensa.config.model.YamlFileIO;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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
        assertThat(lang.getString("no_perms", "")).contains("<#FF6B81>").doesNotContain("<red>");
        assertThat(lang.getString("help_command_format", ""))
                .contains("<#F4C15D>", "<#7AE6B2>", "<#AAB4CC>");
        assertThat(lang.contains("help_desc_tinfo")).isFalse();
    }

    @Test
    void ukrainianLanguagePreservesExistingValuesAndAddsMissingDefaults() throws Exception {
        Files.writeString(tempDir.resolve("config.yml"), "language: uk\n", StandardCharsets.UTF_8);
        Tensa.config = new Config();
        resetLangSingleton();

        Path langFile = tempDir.resolve("lang").resolve("uk.yml");
        Files.createDirectories(langFile.getParent());
        Files.writeString(langFile, """
                no_perms: <red>You do not have permission to use this command</red>
                rcon_usage: <gold>Usage:</gold> <yellow>rcon</yellow> <gray>[server/all/reload] [command]</gray>
                """, StandardCharsets.UTF_8);

        LangYAML lang = LangYAML.getInstance();

        assertThat(lang.getString("no_perms", "")).contains("permission");
        assertThat(lang.getString("rcon_usage", "")).contains("Usage");
        assertThat(lang.getString("reload", "")).contains("перезавантажено").doesNotContain("reloaded");
        assertThat(lang.getString("player_time", "")).contains("Ваш час гри").doesNotContain("Your game time");
        assertThat(lang.getString("send_success", "")).contains("надіслано").doesNotContain("sent to server");
    }

    @Test
    void bundledUkrainianDefaultsAddDiscordLinkTemplateWithoutOverwritingManualTranslations() throws Exception {
        Files.writeString(tempDir.resolve("config.yml"), "language: uk\n", StandardCharsets.UTF_8);
        Tensa.config = new Config();
        resetLangSingleton();

        Path langFile = tempDir.resolve("lang").resolve("uk.yml");
        Files.createDirectories(langFile.getParent());
        Files.writeString(langFile, """
                discord_usage: "<gold>Мій ручний переклад</gold>"
                """, StandardCharsets.UTF_8);

        LangYAML lang = LangYAML.getInstance();

        assertThat(lang.getString("discord_usage", "")).isEqualTo("<gold>Мій ручний переклад</gold>");
        assertThat(lang.getString("discord_link_code", ""))
                .contains("copy_to_clipboard:'{code}'")
                .contains("[Скопіювати]");
    }

    @Test
    void generatedLanguageCanReloadWithoutCorruptRecovery() throws Exception {
        LangYAML lang = LangYAML.getInstance();
        Path langFile = tempDir.resolve("lang").resolve("en.yml");

        assertThatCode(() -> YamlFileIO.load(YamlFileIO.loader(langFile)))
                .doesNotThrowAnyException();

        lang.getReloadedFile();

        try (var files = Files.list(tempDir.resolve("lang"))) {
            assertThat(files.map(path -> path.getFileName().toString()))
                    .noneMatch(name -> name.contains(".corrupt."));
        }
    }

    @Test
    void changingConfiguredLanguageReloadsTheCorrectFileWithoutRestart() throws Exception {
        Files.writeString(tempDir.resolve("config.yml"), "language: en\n");
        Tensa.config = new Config();
        LangYAML english = LangYAML.getInstance();
        Files.writeString(tempDir.resolve("config.yml"), "language: uk\n");
        Tensa.config.reload();

        LangYAML ukrainian = LangYAML.getInstance();

        assertThat(ukrainian.getFilePath()).endsWith("uk.yml");
        assertThat(ukrainian.getString("reload", "")).contains("перезавантажено");
        assertThat(Files.readString(Path.of(english.getFilePath()))).contains("reloaded");
    }

    @Test
    void synchronizingEnglishDefaultsDoesNotPopulateUkrainianWithEnglishText() throws Exception {
        LangYAML english = LangYAML.getInstance();
        Path ukrainian = tempDir.resolve("lang/uk.yml");
        Files.writeString(ukrainian, "prefix: ''\n");

        LangYAML.syncAllLanguageFiles(english.getConfig());

        var saved = YamlFileIO.load(YamlFileIO.loader(ukrainian));
        assertThat(saved.node("reload").getString()).contains("перезавантажено");
    }

    @Test
    void corruptTranslationIsNotReplacedDuringReloadOrCatalogSync() throws Exception {
        LangYAML english = LangYAML.getInstance();
        Path englishFile = Path.of(english.getFilePath());
        String broken = "no_perms: [\n";
        Files.writeString(englishFile, broken);
        org.assertj.core.api.Assertions.assertThatThrownBy(LangYAML::getInstance)
                .isInstanceOf(IllegalStateException.class);
        LangYAML.syncAllLanguageFiles(english.getConfig());
        assertThat(Files.readString(englishFile)).isEqualTo(broken);
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

    private void resetLangSingleton() throws Exception {
        Field instance = LangYAML.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, null);
    }
}
