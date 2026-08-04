package ua.co.tensa.modules.discord;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;

record DiscordRelayGuard(
        boolean enabled,
        Set<Direction> directions,
        Mode mode,
        Set<String> channels,
        String minecraftMessage,
        String discordReply,
        Duration discordReplyDeleteAfter,
        Duration feedbackCooldown
) {
    enum Direction {
        MINECRAFT_TO_DISCORD,
        DISCORD_TO_MINECRAFT;

        static Direction parse(String value) {
            return switch (normalize(value)) {
                case "minecraft_to_discord" -> MINECRAFT_TO_DISCORD;
                case "discord_to_minecraft" -> DISCORD_TO_MINECRAFT;
                default -> throw new DiscordConfigurationException(
                        "discord.yml proxy_chat.require_link_to_relay.directions contains an unsupported direction"
                );
            };
        }
    }

    enum Mode {
        ONLY,
        EXCEPT;

        static Mode parse(String value) {
            return switch (normalize(value)) {
                case "only" -> ONLY;
                case "except" -> EXCEPT;
                default -> throw new DiscordConfigurationException(
                        "discord.yml proxy_chat.require_link_to_relay.mode must be only or except"
                );
            };
        }
    }

    DiscordRelayGuard {
        directions = Set.copyOf(directions);
        channels = Set.copyOf(channels);
    }

    boolean protects(Direction direction, String logicalChannel) {
        if (!enabled || !directions.contains(direction)) {
            return false;
        }
        boolean listed = channels.contains(normalize(logicalChannel));
        return mode == Mode.ONLY ? listed : !listed;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
