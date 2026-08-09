package ua.co.tensa.modules.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageRendererTest {

    @Test
    void rendersUrlsAsOpenUrlComponentsWithoutAllowingMiniMessageInjection() {
        String raw = "дивись https://example.com/path?q=1. <click:run_command:'/op attacker'>ні</click>";
        String format = "<gray>{from}: {message}</gray> "
                + "<click:copy_to_clipboard:'{message_payload}'>copy</click>";

        ChatMessageRenderer.Result rendered = ChatMessageRenderer.render(
                format,
                Map.of("from", "Sender"),
                raw
        );

        List<ClickEvent> clicks = new ArrayList<>();
        collectClicks(rendered.component(), clicks);
        assertThat(clicks).noneMatch(click -> click.action() == ClickEvent.Action.RUN_COMMAND);
        assertThat(clicks.stream()
                .filter(click -> click.action() == ClickEvent.Action.OPEN_URL)
                .map(click -> ((ClickEvent.Payload.Text) click.payload()).value()))
                .containsExactly("https://example.com/path?q=1");
        assertThat(clicks.stream()
                .filter(click -> click.action() == ClickEvent.Action.COPY_TO_CLIPBOARD)
                .map(click -> ((ClickEvent.Payload.Text) click.payload()).value()))
                .containsExactly(raw);
        assertThat(plainText(rendered.component())).contains(raw);
        assertThat(rendered.clickableUrls()).isEqualTo(1);
    }

    @Test
    void leavesNonHttpTokensAsPlainText() {
        ChatMessageRenderer.Result rendered = ChatMessageRenderer.render(
                "<white>{message}</white>",
                Map.of(),
                "example.com ftp://example.com"
        );

        List<ClickEvent> clicks = new ArrayList<>();
        collectClicks(rendered.component(), clicks);
        assertThat(clicks).isEmpty();
        assertThat(rendered.clickableUrls()).isZero();
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
