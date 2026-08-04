package ua.co.tensa.modules.discord;

import java.net.URI;
import java.net.URISyntaxException;
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
        String thumbnailUrl,
        String footer,
        String imageUrl
) {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z][a-z0-9_]*)}");

    static DiscordEmbedTemplate parse(
            String key,
            Map<String, Object> raw,
            Set<String> allowedPlaceholders
    ) {
        boolean enabled = booleanValue(raw.get("enabled"), false);
        String title = stringValue(raw.get("title"));
        String description = stringValue(raw.get("description"));
        String footer = stringValue(raw.get("footer"));
        String thumbnail = stringValue(raw.get("thumbnail_url"));
        String image = stringValue(raw.get("image_url"));
        validateLength(key + ".title", title, 1, 256);
        validateLength(key + ".description", description, 1, 4_096);
        validateLength(key + ".footer", footer, 0, 2_048);
        validatePlaceholders(key + ".title", title, allowedPlaceholders);
        validatePlaceholders(key + ".description", description, allowedPlaceholders);
        validatePlaceholders(key + ".footer", footer, allowedPlaceholders);
        validateUrl(key + ".thumbnail_url", thumbnail, allowedPlaceholders);
        validateUrl(key + ".image_url", image, allowedPlaceholders);
        return new DiscordEmbedTemplate(
                enabled, title, description, parseColor(key, raw.get("color")), thumbnail, footer, image
        );
    }

    DiscordEmbedMessage render(Map<String, String> values) {
        return new DiscordEmbedMessage(
                DiscordMessages.render(title, values),
                DiscordMessages.render(description, values),
                color,
                java.time.Instant.now(),
                renderUrl("thumbnail_url", thumbnailUrl, values),
                DiscordMessages.render(footer, values),
                renderUrl("image_url", imageUrl, values)
        );
    }

    private static String renderUrl(String key, String template, Map<String, String> values) {
        String rendered = DiscordMessages.render(template, values);
        if (rendered.isBlank()) {
            return "";
        }
        try {
            URI uri = new URI(rendered);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalArgumentException("Rendered Discord embed " + key + " is not HTTPS");
            }
            return rendered;
        } catch (URISyntaxException invalid) {
            throw new IllegalArgumentException("Rendered Discord embed " + key + " is invalid", invalid);
        }
    }

    private static int parseColor(String key, Object value) {
        if (value instanceof Number number) {
            int color = number.intValue();
            if (color >= 0 && color <= 0xFFFFFF) {
                return color;
            }
        }
        String text = stringValue(value);
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

    private static void validateUrl(String key, String template, Set<String> allowed) {
        if (template.isBlank()) {
            return;
        }
        validateLength(key, template, 0, 2_048);
        validatePlaceholders(key, template, allowed);
        String sample = PLACEHOLDER.matcher(template).replaceAll("sample");
        try {
            URI uri = new URI(sample);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new DiscordConfigurationException("discord.yml " + key + " must be an HTTPS URL");
            }
        } catch (URISyntaxException invalid) {
            throw new DiscordConfigurationException("discord.yml " + key + " is invalid");
        }
    }

    private static void validateLength(String key, String value, int minimum, int maximum) {
        int length = value.codePointCount(0, value.length());
        if (length < minimum || length > maximum) {
            throw new DiscordConfigurationException(
                    "discord.yml " + key + " must contain " + minimum + "-" + maximum + " characters");
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : DiscordSanitizer.normalize(String.valueOf(value)).trim();
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }
}
