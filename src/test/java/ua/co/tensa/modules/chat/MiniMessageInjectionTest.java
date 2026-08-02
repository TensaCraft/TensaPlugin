package ua.co.tensa.modules.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.co.tensa.Tensa;
import ua.co.tensa.modules.chat.data.ChatConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MiniMessageInjectionTest {
    @TempDir
    Path tempDir;

    @Test
    void privateMessageCannotBreakOutOfQuotedClickPayload() {
        Tensa.pluginPath = tempDir;
        ChatConfig config = new ChatConfig();
        config.reloadCfg();
        String template = config.adapter().getString("private.to_format", "");
        String malicious = "hello'></click><click:run_command:'/op attacker'>owned";

        String rendered = ua.co.tensa.Message.renderTemplateString(
                template,
                ChatCommands.privateCtx("lobby", "Sender", "Target", malicious)
        );
        Component component = MiniMessage.miniMessage().deserialize(rendered);
        List<ClickEvent> clicks = new ArrayList<>();
        collectClicks(component, clicks);

        assertThat(clicks).noneMatch(click -> click.action() == ClickEvent.Action.RUN_COMMAND);
        assertThat(clicks.stream()
                .filter(click -> click.action() == ClickEvent.Action.COPY_TO_CLIPBOARD)
                .map(click -> ((ClickEvent.Payload.Text) click.payload()).value()))
                .containsExactly(malicious);
        assertThat(plainText(component)).contains(malicious);
    }

    private static void collectClicks(Component component, List<ClickEvent> clicks) {
        if (component.clickEvent() != null) {
            clicks.add(component.clickEvent());
        }
        component.children().forEach(child -> collectClicks(child, clicks));
    }

    private static String plainText(Component component) {
        StringBuilder text = new StringBuilder();
        collectText(component, text);
        return text.toString();
    }

    private static void collectText(Component component, StringBuilder text) {
        if (component instanceof TextComponent content) {
            text.append(content.content());
        }
        component.children().forEach(child -> collectText(child, text));
    }
}
