package ua.co.tensa.modules.discord;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validated, typed Discord embed template prepared before runtime activation. */
record DiscordEmbedTemplate(
        boolean enabled,
        String title,
        String description,
        int color,
        String footer
) {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z][a-z0-9_]*)}");

    static DiscordEmbedTemplate parse(
            String key,
            Map<String, Object> raw,
            Set<String> allowedPlaceholders
    ) {
        boolean enabled = booleanValue(key + ".enabled", raw.get("enabled"), false);
        String title = stringValue(key + ".title", raw.get("title"));
        String description = stringValue(key + ".description", raw.get("description"));
        String footer = stringValue(key + ".footer", raw.get("footer"));
        validateLength(key + ".title", title, 1, 256);
        validateLength(key + ".description", description, 1, 4_096);
        validateLength(key + ".footer", footer, 0, 2_048);
        validatePlaceholders(key + ".title", title, allowedPlaceholders);
        validatePlaceholders(key + ".description", description, allowedPlaceholders);
        validatePlaceholders(key + ".footer", footer, allowedPlaceholders);
        return new DiscordEmbedTemplate(enabled, title, description, parseColor(key, raw.get("color")), footer);
    }

    DiscordEmbedMessage render(Map<String, String> values) {
        return new DiscordEmbedMessage(
                DiscordMessages.render(title, values),
                DiscordMessages.render(description, values),
                color,
                java.time.Instant.now(),
                "",
                DiscordMessages.render(footer, values)
        );
    }

    private static int parseColor(String key, Object value) {
        if (value instanceof Number number) {
            int color = number.intValue();
            if (color >= 0 && color <= 0xFFFFFF) {
                return color;
            }
        }
        String text = stringValue(key + ".color", value);
        if (text.matches("#[0-9a-fA-F]{6}")) {
            return Integer.parseInt(text.substring(1), 16);
        }
        throw new DiscordConfigurationException("discord.yml " + key + ".color must be #RRGGBB");
    }

    private static void validatePlaceholders(String key, String template, Set<String> allowed) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        LinkedHashSet<String> found = new LinkedHashSet<>();
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        found.removeAll(allowed);
        if (!found.isEmpty()) {
            throw new DiscordConfigurationException(
                    "discord.yml " + key + " contains unsupported placeholders: " + String.join(",", found));
        }
    }

    private static void validateLength(String key, String value, int minimum, int maximum) {
        int length = value.codePointCount(0, value.length());
        if (length < minimum || length > maximum) {
            throw new DiscordConfigurationException(
                    "discord.yml " + key + " must contain " + minimum + "-" + maximum + " characters");
        }
    }

    private static String stringValue(String key, Object value) {
        if (value == null) {
            return "";
        }
        if (!(value instanceof String text)) {
            throw new DiscordConfigurationException("discord.yml " + key + " must be a string");
        }
        return DiscordSanitizer.normalize(text).trim();
    }

    private static boolean booleanValue(String key, Object value, boolean fallback) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value == null) {
            return fallback;
        }
        throw new DiscordConfigurationException("discord.yml " + key + " must be true or false");
    }
}
