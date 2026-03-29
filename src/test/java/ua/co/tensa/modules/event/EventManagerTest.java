package ua.co.tensa.modules.event;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EventManagerTest {

    @Test
    void renderCommandsReplacesExtendedEventPlaceholders() {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("event", "on_server_pre_connect");
        placeholders.put("player", "Steve");
        placeholders.put("uuid", "00000000-0000-0000-0000-000000000000");
        placeholders.put("server", "hub");
        placeholders.put("fromServer", "auth");
        placeholders.put("toServer", "hub");
        placeholders.put("originalServer", "hub");
        placeholders.put("initialServer", "hub");
        placeholders.put("ip", "127.0.0.1");
        placeholders.put("host", "play.example.com");
        placeholders.put("port", "25565");
        placeholders.put("address", "127.0.0.1:25565");
        placeholders.put("protocol", "MINECRAFT_1_21_5");
        placeholders.put("intent", "LOGIN");
        placeholders.put("listener", "MINECRAFT");
        placeholders.put("firstJoinAt", "2026-03-29T12:00:00Z");

        List<String> rendered = EventManager.renderCommands(List.of(
                "/alert {event} {player} {server} {fromServer} {toServer} {originalServer} {initialServer} {ip} {host} {port} {address} {protocol} {intent} {listener} {firstJoinAt}"
        ), placeholders);

        assertThat(rendered).containsExactly(
                "/alert on_server_pre_connect Steve hub auth hub hub hub 127.0.0.1 play.example.com 25565 127.0.0.1:25565 MINECRAFT_1_21_5 LOGIN MINECRAFT 2026-03-29T12:00:00Z"
        );
    }
}
