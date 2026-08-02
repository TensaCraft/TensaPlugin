package ua.co.tensa.modules.discord;

import java.time.Instant;

record DiscordEmbedMessage(String title, String description, int color, Instant timestamp) {
    static final int BLURPLE = 0x5865F2;
    static final int GREEN = 0x57F287;
    static final int YELLOW = 0xFEE75C;
    static final int RED = 0xED4245;
    static final int GRAY = 0x95A5A6;

    DiscordEmbedMessage {
        title = DiscordSanitizer.truncate(DiscordSanitizer.normalize(title), 256);
        description = DiscordSanitizer.truncate(DiscordSanitizer.normalize(description), 4_096);
        timestamp = timestamp == null ? Instant.now() : timestamp;
        if (title.isBlank() || description.isBlank()) {
            throw new IllegalArgumentException("Discord embed title and description must not be blank");
        }
    }

    static DiscordEmbedMessage of(String title, String description, int color) {
        return new DiscordEmbedMessage(title, description, color, Instant.now());
    }

    static DiscordEmbedMessage success(String description) {
        return of("Прив'язку завершено", description, GREEN);
    }

    static DiscordEmbedMessage linkError(String description) {
        return of("Не вдалося прив'язати акаунт", description, RED);
    }
}
