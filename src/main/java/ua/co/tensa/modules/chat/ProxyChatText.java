package ua.co.tensa.modules.chat;

final class ProxyChatText {
    private ProxyChatText() {
    }

    static String normalize(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        StringBuilder output = new StringBuilder(input.length());

        for (int offset = 0; offset < input.length(); ) {
            int codePoint = input.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (Character.isISOControl(codePoint)) {
                continue;
            }

            output.appendCodePoint(codePoint);
        }

        return output.toString().trim();
    }

    static String boundedIdentity(String input, int maxCodePoints) {
        String normalized = normalize(input);
        if (normalized.isEmpty() || maxCodePoints <= 0) {
            return "";
        }
        int count = normalized.codePointCount(0, normalized.length());
        if (count <= maxCodePoints) {
            return normalized;
        }
        return normalized.substring(0, normalized.offsetByCodePoints(0, maxCodePoints));
    }
}
