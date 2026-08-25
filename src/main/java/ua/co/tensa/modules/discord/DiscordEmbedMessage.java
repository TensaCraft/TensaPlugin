package ua.co.tensa.modules.discord;

import java.time.Instant;

record DiscordEmbedMessage(
        String title,
        String description,
        int color,
        Instant timestamp,
        String footer,
        String footerIconUrl
) {
    static final int BLURPLE = 0x5865F2;
    static final int GREEN = 0x57F287;
    static final int YELLOW = 0xFEE75C;
    static final int RED = 0xED4245;
    static final int GRAY = 0x95A5A6;

    DiscordEmbedMessage {
        title = DiscordSanitizer.truncate(DiscordSanitizer.normalize(title), 256);
        description = DiscordSanitizer.truncate(DiscordSanitizer.normalize(description), 4_096);
        footer = normalizeOptional(footer, 2_048);
        footerIconUrl = footer.isBlank() ? "" : normalizeOptional(footerIconUrl, 2_048);
        timestamp = timestamp == null ? Instant.now() : timestamp;
        if (title.isBlank() || description.isBlank()) {
            throw new IllegalArgumentException("Discord embed title and description must not be blank");
        }
    }

    DiscordEmbedMessage(String title, String description, int color, Instant timestamp) {
        this(title, description, color, timestamp, "", "");
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

    DiscordEmbedMessage withFooterIcon(String url) {
        return new DiscordEmbedMessage(title, description, color, timestamp, footer, url);
    }

    private static String normalizeOptional(String value, int maximum) {
        return DiscordSanitizer.truncate(DiscordSanitizer.normalize(value), maximum).trim();
    }
}
