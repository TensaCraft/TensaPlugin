package ua.co.tensa.modules.discord;

public record DiscordInboundMessage(
        String guildId,
        String channelId,
        String authorId,
        String authorName,
        String content,
        boolean bot,
        boolean webhook
) {
}
