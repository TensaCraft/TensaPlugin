package ua.co.tensa.placeholders.providers;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import ua.co.tensa.Message;
import ua.co.tensa.placeholders.PlaceholderProvider;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Integration with PAPIProxyBridge by William278, per official docs.
 * https://github.com/WiIIiam278/PAPIProxyBridge
 */
public class PAPIProxyBridgeProvider implements PlaceholderProvider {

    private net.william278.papiproxybridge.api.PlaceholderAPI apiInstance; // direct API per docs

    public PAPIProxyBridgeProvider() {
        try {
            // Per README: PlaceholderAPI.createInstance() / formatPlaceholders(...)
            apiInstance = net.william278.papiproxybridge.api.PlaceholderAPI.createInstance();
            Message.info("PAPIProxyBridge: API detected (net.william278.papiproxybridge.api.PlaceholderAPI)");
        } catch (Throwable e) {
            apiInstance = null;
            // keep silent to avoid noise; availability will be false
        }
    }

    @Override
    public boolean isAvailable() {
        return apiInstance != null;
    }

    @Override
    public String resolveRaw(Player player, String input) {
        if (!isAvailable() || input == null || input.isEmpty()) return input;
        try {
            if (player == null) return input;
            UUID uuid = player.getUniqueId();
            CompletableFuture<String> fut = apiInstance.formatPlaceholders(input, uuid);
            // Non-blocking in main usage; here we best-effort with minimal wait
            return fut.getNow(input);
        } catch (Throwable e) {
            Message.warn("PAPIProxyBridge resolve failed: " + e.getMessage());
            return input;
        }
    }

    @Override
    public Component resolveComponent(Player player, String input) {
        if (!isAvailable()) {
            return Message.convert(resolveRaw(player, input));
        }
        try {
            if (player == null) return Message.convert(input);
            UUID uuid = player.getUniqueId();
            CompletableFuture<Component> fut = apiInstance.formatComponentPlaceholders(input, uuid);
            return fut.getNow(Message.convert(input));
        } catch (Throwable e) {
            Message.warn("PAPIProxyBridge component resolve failed: " + e.getMessage());
            return Message.convert(input);
        }
    }

    // Async helpers
    public CompletableFuture<String> formatPlaceholdersAsync(Player player, String input) {
        if (!isAvailable() || player == null || input == null) return CompletableFuture.completedFuture(input);
        try {
            return apiInstance.formatPlaceholders(input, player.getUniqueId());
        } catch (Throwable e) {
            Message.warn("PAPIProxyBridge resolve failed: " + e.getMessage());
            return CompletableFuture.completedFuture(input);
        }
    }

    public CompletableFuture<Component> formatComponentAsync(Player player, String input) {
        if (!isAvailable() || player == null || input == null) return CompletableFuture.completedFuture(Message.convert(input));
        try {
            return apiInstance.formatComponentPlaceholders(input, player.getUniqueId());
        } catch (Throwable e) {
            Message.warn("PAPIProxyBridge component resolve failed: " + e.getMessage());
            return CompletableFuture.completedFuture(Message.convert(input));
        }
    }
}
