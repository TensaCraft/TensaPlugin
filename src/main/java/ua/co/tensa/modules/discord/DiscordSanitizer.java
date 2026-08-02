package ua.co.tensa.modules.discord;

import java.util.Set;

public final class DiscordSanitizer {
    private static final Set<Integer> DISCORD_MARKDOWN = Set.of(
            (int) '\\', (int) '`', (int) '*', (int) '_', (int) '{', (int) '}',
            (int) '[', (int) ']', (int) '(', (int) ')', (int) '#', (int) '+',
            (int) '-', (int) '.', (int) '!', (int) '|', (int) '>', (int) '~'
    );

    private DiscordSanitizer() {
    }

    public static String forMinecraft(String input, int maxCodePoints) {
        String plain = normalize(input);
        StringBuilder safe = new StringBuilder(plain.length());
        plain.codePoints().forEach(codePoint -> {
            if (codePoint == '<') {
                safe.append('\uFF1C');
            } else if (codePoint == '>') {
                safe.append('\uFF1E');
            } else if (codePoint == '&' || codePoint == '\u00A7') {
                safe.append('\uFF06');
            } else {
                safe.appendCodePoint(codePoint);
            }
        });
        return truncate(safe.toString(), maxCodePoints);
    }

    public static String forDiscord(String input, int maxCodePoints) {
        String plain = truncate(normalize(input), maxCodePoints);
        StringBuilder safe = new StringBuilder(plain.length() + 16);
        plain.codePoints().forEach(codePoint -> {
            if (codePoint == '@') {
                safe.append("@\u200B");
            } else if (DISCORD_MARKDOWN.contains(codePoint)) {
                safe.append('\\').appendCodePoint(codePoint);
            } else {
                safe.appendCodePoint(codePoint);
            }
        });
        return safe.toString();
    }

    public static String webhookUsername(String input) {
        String username = truncate(normalize(input), 80).replace('@', '\uFF20');
        return username.isBlank() ? "Minecraft" : username;
    }

    static String normalize(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        StringBuilder result = new StringBuilder(input.length());
        boolean previousWhitespace = false;
        for (int offset = 0; offset < input.length();) {
            int codePoint = input.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                if (!previousWhitespace && !result.isEmpty()) {
                    result.append(' ');
                }
                previousWhitespace = true;
                continue;
            }
            if (isUnsafe(codePoint)) {
                continue;
            }
            previousWhitespace = false;
            result.appendCodePoint(codePoint);
        }
        return result.toString().trim();
    }

    static String truncate(String input, int maxCodePoints) {
        if (input == null || maxCodePoints <= 0) {
            return "";
        }
        int count = input.codePointCount(0, input.length());
        if (count <= maxCodePoints) {
            return input;
        }
        int end = input.offsetByCodePoints(0, maxCodePoints);
        return input.substring(0, end);
    }

    private static boolean isUnsafe(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isISOControl(codePoint)
                || type == Character.CONTROL
                || type == Character.FORMAT
                || type == Character.PRIVATE_USE
                || type == Character.SURROGATE
                || type == Character.UNASSIGNED;
    }
}
