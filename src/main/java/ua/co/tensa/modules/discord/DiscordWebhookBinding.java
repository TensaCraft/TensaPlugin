package ua.co.tensa.modules.discord;

record DiscordWebhookBinding(DiscordRoute route, String webhookId, String tokenFingerprint, long updatedAtMillis) {
    @Override
    public String toString() {
        return "DiscordWebhookBinding[route=" + route + ", webhookId=" + webhookId + "]";
    }
}
