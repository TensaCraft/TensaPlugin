package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscordEmbedTemplateTest {
    @Test
    void validatesAndRendersOnlyPresentationFields() {
        Map<String, Object> raw = valid();
        raw.put("footer", "Server {server}");

        DiscordEmbedTemplate template = DiscordEmbedTemplate.parse(
                "embeds.join", raw, Set.of("player", "server"));
        DiscordEmbedMessage rendered = template.render(Map.of("player", "Pilot", "server", "Aero"));

        assertThat(rendered.title()).isEqualTo("Welcome Pilot");
        assertThat(rendered.description()).isEqualTo("Pilot joined Aero");
        assertThat(rendered.footer()).isEqualTo("Server Aero");
        assertThat(rendered.footerIconUrl()).isEmpty();
        assertThat(rendered.color()).isEqualTo(0x57F287);
    }

    @Test
    void dropsFooterIconWhenFooterTextIsMissing() {
        DiscordEmbedMessage embed = DiscordEmbedMessage.of("Title", "Description", 0x123456)
                .withFooterIcon("https://example.com/icon.png");

        assertThat(embed.footer()).isEmpty();
        assertThat(embed.footerIconUrl()).isEmpty();
    }

    @Test
    void rejectsUnknownPlaceholderAndDiscordLimitOverflow() {
        Map<String, Object> placeholder = valid();
        placeholder.put("description", "{unknown}");
        assertThatThrownBy(() -> DiscordEmbedTemplate.parse(
                "embeds.join", placeholder, Set.of("player", "server")))
                .isInstanceOf(DiscordConfigurationException.class)
                .hasMessageContaining("unsupported placeholders");

        Map<String, Object> oversized = valid();
        oversized.put("title", "x".repeat(257));
        assertThatThrownBy(() -> DiscordEmbedTemplate.parse(
                "embeds.join", oversized, Set.of("player", "server")))
                .isInstanceOf(DiscordConfigurationException.class)
                .hasMessageContaining("256");
    }

    @Test
    void rejectsNestedObjectsInsteadOfStringifyingThem() {
        Map<String, Object> raw = valid();
        raw.put("description", Map.of());

        assertThatThrownBy(() -> DiscordEmbedTemplate.parse(
                "embeds.join", raw, Set.of("player", "server")))
                .isInstanceOf(DiscordConfigurationException.class)
                .hasMessageContaining("embeds.join.description")
                .hasMessageContaining("string");
    }

    private static Map<String, Object> valid() {
        LinkedHashMap<String, Object> raw = new LinkedHashMap<>();
        raw.put("enabled", true);
        raw.put("title", "Welcome {player}");
        raw.put("description", "{player} joined {server}");
        raw.put("color", "#57F287");
        raw.put("footer", "");
        return raw;
    }
}
