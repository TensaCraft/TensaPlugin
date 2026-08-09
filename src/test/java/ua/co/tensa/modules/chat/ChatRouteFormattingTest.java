package ua.co.tensa.modules.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.co.tensa.Tensa;
import ua.co.tensa.modules.chat.data.ChatConfig;
import ua.co.tensa.modules.discord.data.DiscordConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;

import static org.assertj.core.api.Assertions.assertThat;

class ChatRouteFormattingTest {

    @TempDir
    Path tempDir;

    @Test
    void pmUsesThePrivateRouteAndGlobalUsesItsChatsYamlFormat() {
        Tensa.pluginPath = tempDir;
        ChatConfig chats = new ChatConfig();
        chats.reloadCfg();
        DiscordConfig discord = new DiscordConfig();
        discord.reloadCfg();
        ProxyChatService service = new ProxyChatService(chats.adapter(), discord.adapter(), null);
        ChatCommands commands = new ChatCommands(chats.adapter(), service);

        assertThat(commands.findRoute("pm"))
                .isEqualTo(new ChatCommands.ChatRoute("private", true, false));
        assertThat(service.resolveChannel("global").format())
                .isEqualTo(chats.getString("global.format", ""));
        assertThat(service.resolveChannel("global").relayToDiscord()).isTrue();
        assertThat(service.resolveChannel("staff").relayToDiscord()).isFalse();

        String raw = "secret <click:run_command:'/op attacker'>payload</click>";
        ChatCommands.PrivateMessages privateMessages = ChatCommands.renderPrivateMessages(
                chats.adapter().getSection("private"),
                Map.of("server", "aero", "from", "Pilot", "to", "Copilot", "target", "Copilot"),
                raw
        );
        assertThat(plain(privateMessages.toRecipient().component())).contains("Pilot", raw);
        assertThat(plain(privateMessages.toSender().component())).contains("Copilot", raw);
        List<ClickEvent> clicks = new ArrayList<>();
        collect(privateMessages.toRecipient().component(), clicks);
        collect(privateMessages.toSender().component(), clicks);
        assertThat(clicks).noneMatch(click -> click.action() == ClickEvent.Action.RUN_COMMAND);
        assertThat(clicks.stream()
                .filter(click -> click.action() == ClickEvent.Action.COPY_TO_CLIPBOARD)
                .map(click -> ((ClickEvent.Payload.Text) click.payload()).value()))
                .containsExactly(raw, raw);
    }

    private static void collect(Component component, List<ClickEvent> clicks) {
        if (component.clickEvent() != null) clicks.add(component.clickEvent());
        component.children().forEach(child -> collect(child, clicks));
    }

    private static String plain(Component component) {
        StringBuilder value = new StringBuilder();
        if (component instanceof TextComponent text) value.append(text.content());
        component.children().forEach(child -> value.append(plain(child)));
        return value.toString();
    }
}
