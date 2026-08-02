package ua.co.tensa.modules.chat;

import java.util.Locale;

final class ProxyChatText {
    private ProxyChatText() {
    }

    static String sanitize(String input, int maxLength, int maxRepeatedCharacters) {
        if (input == null || input.isBlank()) {
            return "";
        }

        int lengthLimit = Math.max(1, Math.min(maxLength, 2_000));
        int repeatLimit = Math.max(1, Math.min(maxRepeatedCharacters, 32));
        StringBuilder output = new StringBuilder(Math.min(input.length(), lengthLimit));
        int previous = -1;
        int repeated = 0;

        for (int offset = 0; offset < input.length() && output.length() < lengthLimit; ) {
            int codePoint = input.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (Character.isISOControl(codePoint)) {
                continue;
            }

            if (codePoint == previous) {
                repeated++;
                if (repeated > repeatLimit) {
                    continue;
                }
            } else {
                previous = codePoint;
                repeated = 1;
            }

            output.appendCodePoint(codePoint);
        }

        return output.toString().trim();
    }

    static String duplicateKey(String input) {
        return input == null ? "" : input.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
