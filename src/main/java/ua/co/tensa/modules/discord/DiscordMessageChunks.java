package ua.co.tensa.modules.discord;

import java.util.ArrayList;
import java.util.List;

/** Splits Discord text without dropping code points or breaking an escaped token boundary. */
final class DiscordMessageChunks {
    private DiscordMessageChunks() {
    }

    static List<String> split(String content, int maximumCodePoints) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }
        if (maximumCodePoints <= 0) {
            throw new IllegalArgumentException("maximumCodePoints must be greater than zero");
        }
        if (content.codePointCount(0, content.length()) <= maximumCodePoints) {
            return List.of(content);
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < content.length()) {
            int remaining = content.codePointCount(start, content.length());
            int end = remaining <= maximumCodePoints
                    ? content.length()
                    : content.offsetByCodePoints(start, maximumCodePoints);
            if (end < content.length()) {
                int soft = softBoundary(content, start, end, maximumCodePoints / 2);
                if (soft > start) {
                    end = soft;
                }
                end = avoidDanglingEscape(content, start, end);
            }
            chunks.add(content.substring(start, end));
            start = end;
        }
        return List.copyOf(chunks);
    }

    private static int softBoundary(String content, int start, int hardEnd, int minimumCodePoints) {
        int minimum = content.offsetByCodePoints(
                start,
                Math.min(minimumCodePoints, content.codePointCount(start, hardEnd))
        );
        for (int index = hardEnd; index > minimum; index--) {
            char previous = content.charAt(index - 1);
            if (previous == '\n' || Character.isWhitespace(previous)) {
                return index;
            }
        }
        return hardEnd;
    }

    private static int avoidDanglingEscape(String content, int start, int end) {
        int slashes = 0;
        for (int index = end - 1; index >= start && content.charAt(index) == '\\'; index--) {
            slashes++;
        }
        return slashes % 2 == 1 && end - 1 > start ? end - 1 : end;
    }
}
