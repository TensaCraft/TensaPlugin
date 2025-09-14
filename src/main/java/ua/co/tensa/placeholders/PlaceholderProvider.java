package ua.co.tensa.placeholders;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

public interface PlaceholderProvider {
    boolean isAvailable();

    // Resolve legacy-style placeholders (e.g., %key%) to a String
    String resolveRaw(Player player, String input);

    // Resolve to a Component, allowing MiniMessage tags if supported
    Component resolveComponent(Player player, String input);
}

