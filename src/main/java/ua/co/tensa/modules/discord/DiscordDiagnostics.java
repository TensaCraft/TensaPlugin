package ua.co.tensa.modules.discord;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

final class DiscordDiagnostics {
    private static final Pattern WEBHOOK = Pattern.compile(
            "https://(?:canary\\.|ptb\\.)?discord(?:app)?\\.com/api/webhooks/\\d+/[A-Za-z0-9._~-]+",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern BOT_TOKEN = Pattern.compile(
            "[A-Za-z0-9_-]{20,}\\.[A-Za-z0-9_-]{6,}\\.[A-Za-z0-9_-]{20,}"
    );
    private static final int MAX_LENGTH = 400;

    private DiscordDiagnostics() {
    }

    static String describe(Throwable error) {
        Throwable cause = unwrap(error);
        String type = cause == null ? "UnknownFailure" : cause.getClass().getSimpleName();
        String message = cause == null || cause.getMessage() == null ? "no details" : cause.getMessage();
        String sanitized = WEBHOOK.matcher(message).replaceAll("[REDACTED_WEBHOOK]");
        sanitized = BOT_TOKEN.matcher(sanitized).replaceAll("[REDACTED_TOKEN]");
        sanitized = sanitized.replace('\r', ' ').replace('\n', ' ').trim();
        if (sanitized.length() > MAX_LENGTH) {
            sanitized = sanitized.substring(0, MAX_LENGTH) + "...";
        }
        return type + ": " + sanitized;
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
