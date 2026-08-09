package ua.co.tensa.modules.discord;

record DiscordOutboundMessage(
        DiscordRoute route,
        String webhookContent,
        String botContent,
        String webhookUsername,
        String avatarUrl,
        DiscordEmbedMessage embed,
        boolean preferWebhook
) {
    static DiscordOutboundMessage chat(String webhookContent, String username, String avatarUrl) {
        return new DiscordOutboundMessage(DiscordRoute.CHAT, webhookContent, "", username, avatarUrl, null, true);
    }

    static DiscordOutboundMessage announcement(DiscordEmbedMessage embed) {
        String plain = embed.title() + "\n" + embed.description();
        return new DiscordOutboundMessage(DiscordRoute.EVENTS, plain, plain, "", "", embed, false);
    }

    static DiscordOutboundMessage announcementPlain(String content) {
        return new DiscordOutboundMessage(DiscordRoute.EVENTS, content, content, "", "", null, false);
    }
}
