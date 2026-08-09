package ua.co.tensa.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.co.tensa.placeholders.PlaceholderManager;

import static org.assertj.core.api.Assertions.assertThat;

class TextPipelineTest {
    @BeforeEach
    void initialisePlaceholders() {
        PlaceholderManager.reload();
    }

    @Test
    void onePipelineCombinesPlaceholdersLegacyColoursAndMiniMessageActions() {
        Component component = TextPipeline.render(
                null,
                "&a<tensa_name> %tensa_version% <click:open_url:'https://example.com'>open</click>"
        );

        assertThat(TextPipelineChatTest.plain(component)).isEqualTo("Tensa unknown open");
        assertThat(TextPipelineChatTest.clicks(component))
                .singleElement()
                .satisfies(click -> {
                    assertThat(click.action()).isEqualTo(ClickEvent.Action.OPEN_URL);
                    assertThat(((ClickEvent.Payload.Text) click.payload()).value())
                            .isEqualTo("https://example.com");
                });
    }

    @Test
    void operatorVotePayloadKeepsLiteralUsernameAndBuildsClickableUrl() {
        String payload = "<#55ffff>✦</#55ffff> <#f4a15d>{username}</#f4a15d> "
                + "проголосував за сервер на "
                + "<click:open_url:'https://minecraft-ua.com/minecraft/aeronautics'>"
                + "<#55ffff>https://minecraft-ua.com/minecraft/aeronautics</#55ffff></click> "
                + "та отримав бонус!";

        TextPipeline.Rendered rendered = TextPipeline.operator(payload);

        assertThat(TextPipelineChatTest.plain(rendered.component()))
                .isEqualTo("✦ {username} проголосував за сервер на "
                        + "https://minecraft-ua.com/minecraft/aeronautics та отримав бонус!")
                .doesNotContain("click:open_url");
        assertThat(rendered.clickableUrls()).isOne();
        assertThat(TextPipelineChatTest.clicks(rendered.component()))
                .singleElement()
                .extracting(click -> ((ClickEvent.Payload.Text) click.payload()).value())
                .isEqualTo("https://minecraft-ua.com/minecraft/aeronautics");
    }

    @Test
    void legacyColorsDoNotCorruptAmpersandsInsideUrlsOrClickArguments() {
        String url = "https://example.com/vote?a=1&format=json&b=2";

        TextPipeline.Rendered rendered = TextPipeline.operator(
                "&aVote: <click:open_url:'" + url + "'><aqua>" + url + "</aqua></click>"
        );

        assertThat(TextPipelineChatTest.plain(rendered.component())).isEqualTo("Vote: " + url);
        assertThat(TextPipelineChatTest.clicks(rendered.component()))
                .singleElement()
                .extracting(click -> ((ClickEvent.Payload.Text) click.payload()).value())
                .isEqualTo(url);
    }
}
