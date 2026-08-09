package ua.co.tensa.modules.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import ua.co.tensa.Message;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Renders untrusted chat text as a component while retaining safe URL actions. */
public final class ChatMessageRenderer {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final Pattern HTTP_URL = Pattern.compile("(?i)https?://[^\\s<>]+");
    private static final String MESSAGE_TAG = "tensa_chat_message";
    private static final String TRAILING_PUNCTUATION = ".,!?;:";

    private ChatMessageRenderer() {
    }

    public static Result render(String format, Map<String, String> values, String rawMessage) {
        String template = format == null || format.isBlank() ? "{message}" : format;
        if (values != null) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String value = entry.getValue() == null ? "" : entry.getValue();
                template = template.replace("{" + entry.getKey() + "}", Message.escapeMiniMessage(value));
            }
        }
        String message = rawMessage == null ? "" : rawMessage;
        template = template.replace("{message_payload}", Message.escapeMiniMessageArgument(message));
        template = template.replace("{message}", "<" + MESSAGE_TAG + ">");

        Linkified linkified = linkify(message);
        Component component = MINI.deserialize(
                template,
                TagResolver.resolver(MESSAGE_TAG, Tag.inserting(linkified.component()))
        );
        return new Result(component, linkified.urls());
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
            if (TRAILING_PUNCTUATION.indexOf(last) >= 0 || last == ')' && unbalancedClosingParenthesis(value, length)) {
                length--;
                continue;
            }
            break;
        }
        return length;
    }

    private static boolean unbalancedClosingParenthesis(String value, int length) {
        int balance = 0;
        for (int i = 0; i < length; i++) {
            if (value.charAt(i) == '(') balance++;
            if (value.charAt(i) == ')') balance--;
        }
        return balance < 0;
    }

    public record Result(Component component, int clickableUrls) {
    }

    private record Linkified(Component component, int urls) {
    }
}
