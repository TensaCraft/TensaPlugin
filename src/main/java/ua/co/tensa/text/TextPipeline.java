package ua.co.tensa.text;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import ua.co.tensa.placeholders.PlaceholderManager;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The single Minecraft text boundary for templates, placeholders, legacy
 * colours, MiniMessage and safe player-authored chat components.
 */
public final class TextPipeline {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Pattern LEGACY_COLOR =
            Pattern.compile("&([0-9a-fk-or])(?![>])", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTTP_URL = Pattern.compile("(?i)https?://[^\\s<>]+");
    private static final Pattern LEGACY_PROTECTED = Pattern.compile("<[^>]*>|(?i:https?://[^\\s<>]+)");
    private static final String CHAT_MESSAGE_TAG = "tensa_chat_message";
    private static final String TRAILING_URL_PUNCTUATION = ".,!?;:";
    private static final Map<Character, String> LEGACY_TAGS = Map.ofEntries(
            Map.entry('0', "<black>"),
            Map.entry('1', "<dark_blue>"),
            Map.entry('2', "<dark_green>"),
            Map.entry('3', "<dark_aqua>"),
            Map.entry('4', "<dark_red>"),
            Map.entry('5', "<dark_purple>"),
            Map.entry('6', "<gold>"),
            Map.entry('7', "<gray>"),
            Map.entry('8', "<dark_gray>"),
            Map.entry('9', "<blue>"),
            Map.entry('a', "<green>"),
            Map.entry('b', "<aqua>"),
            Map.entry('c', "<red>"),
            Map.entry('d', "<light_purple>"),
            Map.entry('e', "<yellow>"),
            Map.entry('f', "<white>"),
            Map.entry('k', "<obfuscated>"),
            Map.entry('l', "<bold>"),
            Map.entry('m', "<strikethrough>"),
            Map.entry('n', "<underlined>"),
            Map.entry('o', "<italic>"),
            Map.entry('r', "<reset>")
    );

    private TextPipeline() {
    }

    /** Parse trusted plugin/operator markup without resolving player placeholders. */
    public static Component trusted(String markup) {
        return parse(markup, TagResolver.empty());
    }

    /** Resolve all registered placeholders once, then parse formatting once. */
    public static Component render(CommandSource context, String markup) {
        Player player = context instanceof Player candidate ? candidate : null;
        return parse(resolvePlaceholders(player, markup), TagResolver.empty());
    }

    /** Async equivalent used for PAPI-backed player placeholder resolution. */
    public static CompletableFuture<Component> renderAsync(CommandSource context, String markup) {
        Player player = context instanceof Player candidate ? candidate : null;
        return resolvePlaceholdersAsync(player, markup)
                .thenApply(resolved -> parse(resolved, TagResolver.empty()));
    }

    /** Resolve registered placeholders without interpreting the result as markup. */
    public static String resolvePlaceholders(Player context, String text) {
        return PlaceholderManager.resolveText(context, nullToEmpty(text));
    }

    /** Async raw placeholder resolution for command and protocol templates. */
    public static CompletableFuture<String> resolvePlaceholdersAsync(Player context, String text) {
        return PlaceholderManager.resolveTextAsync(context, nullToEmpty(text));
    }

    /**
     * Render a trusted operator payload. Explicit MiniMessage click tags remain
     * authoritative; plain URLs are not rewritten because doing so would alter
     * the configured component tree.
     */
    public static Rendered operator(String markup) {
        Component component = parse(resolvePlaceholders(null, markup), TagResolver.empty());
        return new Rendered(component, countOpenUrls(component));
    }

    /**
     * Render a trusted route template around untrusted chat. Named values are
     * escaped as text; the message becomes an inserted Component and cannot
     * create MiniMessage actions. Plain HTTP(S) URLs receive safe OPEN_URL events.
     */
    public static Rendered chat(
            Player placeholderContext,
            String format,
            Map<String, String> values,
            String rawMessage
    ) {
        String template = format == null || format.isBlank() ? "{message}" : format;
        template = resolvePlaceholders(placeholderContext, template);
        if (values != null) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                template = template.replace(
                        "{" + entry.getKey() + "}",
                        escapeText(nullToEmpty(entry.getValue()))
                );
            }
        }

        String message = nullToEmpty(rawMessage);
        template = template.replace("{message_payload}", escapeArgument(message));
        template = template.replace("{message}", "<" + CHAT_MESSAGE_TAG + ">");

        Linkified linkified = linkify(message);
        Component component = parse(
                template,
                TagResolver.resolver(CHAT_MESSAGE_TAG, Tag.inserting(linkified.component()))
        );
        return new Rendered(component, linkified.urls());
    }

    public static Rendered chat(String format, Map<String, String> values, String rawMessage) {
        return chat(null, format, values, rawMessage);
    }

    /** Raw curly-token interpolation for command, HTTP and protocol templates. */
    public static String interpolate(String template, Map<String, String> values) {
        if (template == null || values == null || values.isEmpty()) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace(
                    "{" + entry.getKey() + "}",
                    nullToEmpty(entry.getValue())
            );
        }
        return result;
    }

    /** Raw percent-token interpolation for request templates. */
    public static String interpolatePercent(String template, Map<String, String> values) {
        if (template == null || values == null || values.isEmpty()) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace(
                    "%" + entry.getKey() + "%",
                    nullToEmpty(entry.getValue())
            );
        }
        return result;
    }

    /** Exact token-pair interpolation used by legacy localization call sites. */
    public static String interpolateTokens(String template, String... replacements) {
        if (template == null || replacements == null || replacements.length < 2) {
            return template;
        }
        String result = template;
        for (int index = 0; index < replacements.length - 1; index += 2) {
            String token = replacements[index];
            if (token != null) {
                result = result.replace(token, nullToEmpty(replacements[index + 1]));
            }
        }
        return result;
    }

    public static String escapeText(String value) {
        return value == null ? null : value.replace("\\", "\\\\").replace("<", "\\<");
    }

    public static String escapeArgument(String value) {
        return value == null ? null : value.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"");
    }

    private static Component parse(String markup, TagResolver resolver) {
        String normalized = convertLegacy(nullToEmpty(markup).replace('§', '&'));
        return MINI_MESSAGE.deserialize(normalized, resolver);
    }

    private static String convertLegacy(String input) {
        Matcher protectedRanges = LEGACY_PROTECTED.matcher(input);
        StringBuilder output = new StringBuilder(input.length() + 32);
        int cursor = 0;
        while (protectedRanges.find()) {
            output.append(convertLegacySegment(input.substring(cursor, protectedRanges.start())));
            output.append(protectedRanges.group());
            cursor = protectedRanges.end();
        }
        output.append(convertLegacySegment(input.substring(cursor)));
        return output.toString();
    }

    private static String convertLegacySegment(String input) {
        Matcher matcher = LEGACY_COLOR.matcher(input);
        StringBuilder result = new StringBuilder(input.length() + 32);
        while (matcher.find()) {
            char code = Character.toLowerCase(matcher.group(1).charAt(0));
            matcher.appendReplacement(
                    result,
                    Matcher.quoteReplacement(LEGACY_TAGS.getOrDefault(code, matcher.group(0)))
            );
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static Linkified linkify(String message) {
        Matcher matcher = HTTP_URL.matcher(message);
        Component result = Component.empty();
        int cursor = 0;
        int urls = 0;
        while (matcher.find()) {
            if (matcher.start() > cursor) {
                result = result.append(Component.text(message.substring(cursor, matcher.start())));
            }
            String candidate = matcher.group();
            int usableLength = urlLengthWithoutTrailingPunctuation(candidate);
            String url = candidate.substring(0, usableLength);
            if (!url.isBlank()) {
                result = result.append(Component.text(url)
                        .clickEvent(ClickEvent.openUrl(url))
                        .hoverEvent(HoverEvent.showText(Component.text("Відкрити посилання"))));
                urls++;
            }
            if (usableLength < candidate.length()) {
                result = result.append(Component.text(candidate.substring(usableLength)));
            }
            cursor = matcher.end();
        }
        if (cursor < message.length()) {
            result = result.append(Component.text(message.substring(cursor)));
        }
        return new Linkified(result, urls);
    }

    private static int urlLengthWithoutTrailingPunctuation(String value) {
        int length = value.length();
        while (length > 0) {
            char last = value.charAt(length - 1);
            if (TRAILING_URL_PUNCTUATION.indexOf(last) >= 0
                    || last == ')' && unbalancedClosingParenthesis(value, length)) {
                length--;
                continue;
            }
            break;
        }
        return length;
    }

    private static boolean unbalancedClosingParenthesis(String value, int length) {
        int balance = 0;
        for (int index = 0; index < length; index++) {
            if (value.charAt(index) == '(') balance++;
            if (value.charAt(index) == ')') balance--;
        }
        return balance < 0;
    }

    private static int countOpenUrls(Component component) {
        int count = component.clickEvent() != null
                && component.clickEvent().action() == ClickEvent.Action.OPEN_URL ? 1 : 0;
        for (Component child : component.children()) {
            count += countOpenUrls(child);
        }
        return count;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public record Rendered(Component component, int clickableUrls) {
    }

    private record Linkified(Component component, int urls) {
    }
}
