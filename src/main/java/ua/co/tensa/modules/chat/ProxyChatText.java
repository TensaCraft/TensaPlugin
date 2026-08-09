package ua.co.tensa.modules.chat;

final class ProxyChatText {
    private ProxyChatText() {
    }

    static String sanitize(String input, int maxLength) {
        if (input == null || input.isBlank()) {
            return "";
        }

        int lengthLimit = Math.max(1, Math.min(maxLength, 2_000));
        StringBuilder output = new StringBuilder(Math.min(input.length(), lengthLimit));

        for (int offset = 0; offset < input.length() && output.length() < lengthLimit; ) {
            int codePoint = input.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (Character.isISOControl(codePoint)) {
                continue;
            }

            output.appendCodePoint(codePoint);
        }

        return output.toString().trim();
    }
}
