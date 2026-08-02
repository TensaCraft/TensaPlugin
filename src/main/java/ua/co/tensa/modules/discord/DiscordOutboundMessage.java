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
    static DiscordOutboundMessage chat(String webhookContent, String botContent, String username, String avatarUrl) {
        return new DiscordOutboundMessage(DiscordRoute.CHAT, webhookContent, botContent, username, avatarUrl, null, true);
    }

    static DiscordOutboundMessage announcement(DiscordEmbedMessage embed) {
        return new DiscordOutboundMessage(DiscordRoute.EVENTS, "", "", "", "", embed, true);
    }
}
