package ua.co.tensa.modules.meta;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import ua.co.tensa.Tensa;
import ua.co.tensa.Util;
import ua.co.tensa.config.Database;
import ua.co.tensa.modules.AbstractModule;
import ua.co.tensa.modules.ModuleEntry;
import ua.co.tensa.modules.meta.data.UserMetaConfig;
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
        // Determine storage type from config
        String type = UserMetaConfig.get().storageType;

        Database db = Tensa.database;

        // If using database storage, require DB to be enabled and connected
        if ("database".equalsIgnoreCase(type)) {
            if (Tensa.config == null || !Tensa.config.databaseEnable()) {
                ua.co.tensa.Message.warn("UserMeta module requires database to be enabled");
                return;
            }
            if (db == null || !db.enabled) {
                ua.co.tensa.Message.warn("UserMeta module: database not connected");
                return;
            }
        }

        // For file/memory storage, proceed without DB; constructor handles modes
        store = new UserMetaStore(db);
        store.ensureTable();
        // Track listener via AbstractModule helper, so it is auto-unregistered
        ((AbstractModule) IMPL).registerListener(new UserMetaModule());
        AbstractModule.registerCommand("tmeta", "usermeta", new UserMetaCommand(store));
        // register meta placeholders with PlaceholderManager
        PlaceholderManager.registerRawPrefixResolver("meta_", (player, key) -> {
            if (player == null || store == null) return "";
            return store.get(player.getUniqueId(), key);
        });
        PlaceholderManager.registerRawPrefixResolver("tensa_meta_", (player, key) -> {
            if (player == null || store == null) return "";
            return store.get(player.getUniqueId(), key);
        });
        PlaceholderManager.registerAnglePrefixResolver("meta_", (player, key) -> {
            if (player == null || store == null) return "";
            return store.get(player.getUniqueId(), key);
        });
        PlaceholderManager.registerAnglePrefixResolver("tensa_meta_", (player, key) -> {
            if (player == null || store == null) return "";
            return store.get(player.getUniqueId(), key);
        });
    }

    private static void disableImpl() {
        Util.unregisterCommand("tmeta");
        PlaceholderManager.unregisterRawPrefixResolver("meta_");
        PlaceholderManager.unregisterRawPrefixResolver("tensa_meta_");
        PlaceholderManager.unregisterAnglePrefixResolver("meta_");
        PlaceholderManager.unregisterAnglePrefixResolver("tensa_meta_");
        store = null;
    }

    public static void enable() { IMPL.enable(); }
    public static void disable() { IMPL.disable(); }

    public static UserMetaStore getStore() {
        return store;
    }

    @Subscribe
    public void onJoin(PostLoginEvent event) {
        if (store != null) {
            store.preload(event.getPlayer().getUniqueId());
        }
    }
}
