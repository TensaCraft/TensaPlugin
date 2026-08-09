package ua.co.tensa.placeholders;

import com.velocitypowered.api.proxy.Player;

public interface PlaceholderProvider {
    boolean isAvailable();

    // Resolve legacy-style placeholders (e.g., %key%) to a String
    String resolveRaw(Player player, String input);

}

