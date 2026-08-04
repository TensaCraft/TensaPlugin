package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscordEmbedTemplateTest {
    @Test
    void validatesAndRendersAllTypedFields() {
        Map<String, Object> raw = valid();
        raw.put("thumbnail_url", "https://cdn.example.test/{player}.png");
        raw.put("footer", "Server {server}");

        DiscordEmbedTemplate template = DiscordEmbedTemplate.parse(
                "embeds.join", raw, Set.of("player", "server"));
        DiscordEmbedMessage rendered = template.render(Map.of("player", "Pilot", "server", "Aero"));

        assertThat(rendered.title()).isEqualTo("Welcome Pilot");
        assertThat(rendered.description()).isEqualTo("Pilot joined Aero");
        assertThat(rendered.thumbnailUrl()).isEqualTo("https://cdn.example.test/Pilot.png");
        assertThat(rendered.footer()).isEqualTo("Server Aero");
        assertThat(rendered.color()).isEqualTo(0x57F287);
    }

    @Test
    void rejectsUnknownPlaceholderInsecureUrlAndDiscordLimitOverflow() {
        Map<String, Object> placeholder = valid();
        placeholder.put("description", "{unknown}");
        assertThatThrownBy(() -> DiscordEmbedTemplate.parse(
                "embeds.join", placeholder, Set.of("player", "server")))
                .isInstanceOf(DiscordConfigurationException.class)
                .hasMessageContaining("unsupported placeholders");

        Map<String, Object> insecure = valid();
        insecure.put("image_url", "http://cdn.example.test/image.png");
        assertThatThrownBy(() -> DiscordEmbedTemplate.parse(
                "embeds.join", insecure, Set.of("player", "server")))
                .isInstanceOf(DiscordConfigurationException.class)
                .hasMessageContaining("HTTPS");

        Map<String, Object> oversized = valid();
        oversized.put("title", "x".repeat(257));
        assertThatThrownBy(() -> DiscordEmbedTemplate.parse(
                "embeds.join", oversized, Set.of("player", "server")))
                .isInstanceOf(DiscordConfigurationException.class)
                .hasMessageContaining("256");
    }

    @Test
    void rejectsInvalidUrlProducedByAPlaceholderAtFormattingTime() {
        Map<String, Object> raw = valid();
        raw.put("image_url", "https://cdn.example.test/{player}.png");
        DiscordEmbedTemplate template = DiscordEmbedTemplate.parse(
                "embeds.join", raw, Set.of("player", "server"));

        assertThatThrownBy(() -> template.render(Map.of("player", "bad path", "server", "Aero")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid");
    }

    private static Map<String, Object> valid() {
        LinkedHashMap<String, Object> raw = new LinkedHashMap<>();
        raw.put("enabled", true);
        raw.put("title", "Welcome {player}");
        raw.put("description", "{player} joined {server}");
        raw.put("color", "#57F287");
        raw.put("thumbnail_url", "");
        raw.put("footer", "");
        raw.put("image_url", "");
        return raw;
    }
}
