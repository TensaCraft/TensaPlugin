package ua.co.tensa.config;

import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import ua.co.tensa.config.model.YamlFileIO;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LiveConfigFixturesTest {

    @Test
    void sanitizedLiveFixturesParseWithConfigurate() throws Exception {
        CommentedConfigurationNode config = load("config.yml");
        CommentedConfigurationNode events = load("events.yml");
        CommentedConfigurationNode request = load("requests/link.yml");
        CommentedConfigurationNode rcon = load("rcon/rcon-manager.yml");
        CommentedConfigurationNode lang = load("lang/uk.yml");

        assertThat(config.node("storage", "type").getString()).isEqualTo("auto");
        assertThat(config.node("modules", "request-module").getBoolean()).isTrue();
        assertThat(events.node("events", "on_first_join_commands", "commands").getList(String.class, List.of()))
                .anySatisfy(command -> assertThat(command).contains("<gradient:#ff0000:#ffaa00>"));
        assertThat(request.node("parameters", "api_key").getString()).isEqualTo("REDACTED_API_KEY");
        assertThat(request.node("response", "failure").getList(String.class, List.of()))
                .contains("tell %player_name% <red>%response%</red>");
        assertThat(rcon.node("servers", "lobby", "pass").getString()).isEqualTo("REDACTED_RCON_PASSWORD");
        assertThat(lang.node("help_header").getString()).contains("Доступні команди");
    }

    private CommentedConfigurationNode load(String relative) throws URISyntaxException, org.spongepowered.configurate.ConfigurateException {
        Path path = Path.of(getClass().getResource("/fixtures/tensa-live/" + relative).toURI());
        return YamlFileIO.load(YamlFileIO.loader(path));
    }
}
