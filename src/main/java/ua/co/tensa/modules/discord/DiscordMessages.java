package ua.co.tensa.modules.discord;

import com.velocitypowered.api.command.CommandSource;
import ua.co.tensa.Message;
import ua.co.tensa.config.data.LangYAML;

import java.util.Map;

final class DiscordMessages {
    private DiscordMessages() {
    }

    static void send(CommandSource source, String key, String fallback, Map<String, String> values) {
        String template = fallback;
        try {
            template = LangYAML.getInstance().adapter().getString(key, fallback);
        } catch (RuntimeException ignored) {
            // Startup tests and partial reloads can run before the language service is available.
        }
        Message.send(source, render(template, values));
    }

    static String render(String template, Map<String, String> values) {
        String rendered = template == null ? "" : template;
        if (values == null) {
            return rendered;
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            rendered = rendered.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return rendered;
    }
}
