package ua.co.tensa.modules.discord;

public final class DiscordLoopGuard {
    private DiscordLoopGuard() {
    }

    public static boolean shouldRelay(DiscordInboundMessage message, String guildId, String channelId, String selfUserId) {
        if (message == null || message.bot() || message.webhook()) {
            return false;
        }
        if (message.authorId() != null && message.authorId().equals(selfUserId)) {
            return false;
        }
        if (!guildId.equals(message.guildId()) || !channelId.equals(message.channelId())) {
            return false;
        }
        return message.content() != null && !message.content().isBlank();
    }
}
