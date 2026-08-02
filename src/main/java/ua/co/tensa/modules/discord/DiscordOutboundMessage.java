package ua.co.tensa.modules.discord;

record DiscordOutboundMessage(
        DiscordRoute route,
        String webhookContent,
        String botContent,
        String webhookUsername,
        String avatarUrl,
        boolean preferWebhook
) {
    static DiscordOutboundMessage chat(String webhookContent, String botContent, String username, String avatarUrl) {
        return new DiscordOutboundMessage(DiscordRoute.CHAT, webhookContent, botContent, username, avatarUrl, true);
    }

    static DiscordOutboundMessage announcement(String content) {
        return new DiscordOutboundMessage(DiscordRoute.EVENTS, content, content, "Tensa", "", true);
    }
}
