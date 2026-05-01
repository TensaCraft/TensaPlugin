package ua.co.tensa.modules.meta;

import ua.co.tensa.Util;
import ua.co.tensa.modules.AbstractModule;
import ua.co.tensa.modules.ModuleEntry;
import ua.co.tensa.placeholders.PlaceholderManager;

public class UserMetaModule {
    private static final ModuleEntry IMPL = new AbstractModule(
            "user-meta", "User Meta") {
        @Override protected void onEnable() { UserMetaModule.enableImpl(); }
        @Override protected void onDisable() { UserMetaModule.disableImpl(); }
    };
    public static final ModuleEntry ENTRY = IMPL;
    private static UserMetaStore store;

    private static void enableImpl() {
        store = new UserMetaStore();
        store.ensureTable();
        AbstractModule.registerCommand("tmeta", "usermeta", new UserMetaCommand(store));
        // register meta placeholders with PlaceholderManager
        PlaceholderManager.registerRawPrefixResolver("meta_", (player, key) -> {
            if (player == null || store == null) return "";
            return store.getCached(player.getUniqueId()).getOrDefault(key, "");
        });
        PlaceholderManager.registerRawPrefixResolver("tensa_meta_", (player, key) -> {
            if (player == null || store == null) return "";
            return store.getCached(player.getUniqueId()).getOrDefault(key, "");
        });
        PlaceholderManager.registerAnglePrefixResolver("meta_", (player, key) -> {
            if (player == null || store == null) return "";
            return store.getCached(player.getUniqueId()).getOrDefault(key, "");
        });
        PlaceholderManager.registerAnglePrefixResolver("tensa_meta_", (player, key) -> {
            if (player == null || store == null) return "";
            return store.getCached(player.getUniqueId()).getOrDefault(key, "");
        });
    }

    private static void disableImpl() {
        Util.unregisterCommand("tmeta");
        PlaceholderManager.unregisterRawPrefixResolver("meta_");
        PlaceholderManager.unregisterRawPrefixResolver("tensa_meta_");
        PlaceholderManager.unregisterAnglePrefixResolver("meta_");
        PlaceholderManager.unregisterAnglePrefixResolver("tensa_meta_");
        if (store != null) {
            store.close();
        }
        store = null;
    }

    public static void enable() { IMPL.enable(); }
    public static void disable() { IMPL.disable(); }

    public static UserMetaStore getStore() {
        return store;
    }

    public static void preload(java.util.UUID uuid) {
        if (store != null) {
            store.preloadAsync(uuid);
        }
    }
}
