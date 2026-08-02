package ua.co.tensa.modules.discord;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import ua.co.tensa.config.Config;
import ua.co.tensa.config.data.LangYAML;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordLinkMessageTest {
    @TempDir
    Path tempDir;

    @BeforeEach
    void configureUkrainianRuntimeLanguage() throws Exception {
        Tensa.pluginPath = tempDir;
        Files.writeString(tempDir.resolve("config.yml"), "language: uk\n", StandardCharsets.UTF_8);
        Tensa.config = new Config();
        Field instance = LangYAML.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, null);
    }

    @Test
    void actualDiscordLinkTemplateShowsAButtonThatCopiesOnlyTheCleanCode() {
        String code = "A1B2C3D4";
        String template = LangYAML.getInstance().getString("discord_link_code", "");
        String rendered = DiscordMessages.render(template, Map.of(
                "code", code,
                "command", "link",
                "minutes", "10"
        ));

        Component message = Message.convert(rendered);
        List<Component> clickable = new ArrayList<>();
        collectClickable(message, clickable);

        assertThat(clickable).singleElement().satisfies(component -> {
            assertThat(component.clickEvent()).isNotNull();
            assertThat(component.clickEvent().action()).isEqualTo(ClickEvent.Action.COPY_TO_CLIPBOARD);
            assertThat(component.clickEvent().payload())
                    .isInstanceOfSatisfying(ClickEvent.Payload.Text.class, payload ->
                            assertThat(payload.value()).isEqualTo(code));
            assertThat(visibleText(component)).contains("[Скопіювати]");
        });
    }

    private static void collectClickable(Component component, List<Component> result) {
        if (component.clickEvent() != null) {
            result.add(component);
        }
        component.children().forEach(child -> collectClickable(child, result));
    }

    private static String visibleText(Component component) {
        StringBuilder text = new StringBuilder();
        if (component instanceof TextComponent value) {
            text.append(value.content());
        }
        component.children().forEach(child -> text.append(visibleText(child)));
        return text.toString();
    }
}
