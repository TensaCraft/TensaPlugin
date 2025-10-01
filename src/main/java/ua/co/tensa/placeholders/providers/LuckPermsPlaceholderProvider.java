package ua.co.tensa.placeholders.providers;

import com.velocitypowered.api.proxy.Player;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedDataManager;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import ua.co.tensa.Message;

import java.util.UUID;

/**
 * Integration with LuckPerms API on Velocity.
 * Provides placeholders for prefix, suffix, group, etc.
 */
public class LuckPermsPlaceholderProvider {

    private LuckPerms api;

    public LuckPermsPlaceholderProvider() {
        try {
            api = LuckPermsProvider.get();
            Message.info("LuckPerms API detected - placeholders enabled");
        } catch (Throwable e) {
            api = null;
            // Keep silent to avoid noise
        }
    }

    public boolean isAvailable() {
        return api != null;
    }

    /**
     * Resolve LuckPerms placeholder for a player.
     * Supports: prefix, suffix, group, meta_<key>
     */
    public String resolve(Player player, String placeholder) {
        if (!isAvailable() || player == null || placeholder == null) return "";

        try {
            UUID uuid = player.getUniqueId();
            User user = api.getUserManager().getUser(uuid);
            if (user == null) return "";

            CachedDataManager cachedData = user.getCachedData();
            QueryOptions queryOptions = api.getContextManager().getQueryOptions(player);
            CachedMetaData metaData = cachedData.getMetaData(queryOptions);

            switch (placeholder.toLowerCase()) {
                case "prefix":
                    return metaData.getPrefix() != null ? metaData.getPrefix() : "";
                case "suffix":
                    return metaData.getSuffix() != null ? metaData.getSuffix() : "";
                case "group":
                    String primary = user.getPrimaryGroup();
                    return primary != null ? primary : "";
                default:
                    // Check for meta_<key> pattern
                    if (placeholder.toLowerCase().startsWith("meta_")) {
                        String key = placeholder.substring(5);
                        String value = metaData.getMetaValue(key);
                        return value != null ? value : "";
                    }
                    return "";
            }
        } catch (Throwable e) {
            Message.warn("LuckPerms resolve failed for " + placeholder + ": " + e.getMessage());
            return "";
        }
    }
}
