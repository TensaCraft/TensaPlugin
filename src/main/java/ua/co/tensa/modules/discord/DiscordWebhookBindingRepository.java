package ua.co.tensa.modules.discord;

import ua.co.tensa.core.storage.CoreStorageService;

import java.sql.SQLException;
import java.util.Optional;

/** Stores webhook identity/fingerprint metadata, never the token or URL. */
final class DiscordWebhookBindingRepository implements AutoCloseable {
    private static final String TABLE = "discord_webhook_bindings";
    private final CoreStorageService storage;

    DiscordWebhookBindingRepository(CoreStorageService storage) {
        this.storage = java.util.Objects.requireNonNull(storage, "storage");
    }

    void initialize() {
        storage.createTable(TABLE,
                "route VARCHAR(16) PRIMARY KEY, webhook_id VARCHAR(20) NOT NULL, "
                        + "token_fingerprint CHAR(64) NOT NULL, updated_at BIGINT NOT NULL");
    }

    Optional<DiscordWebhookBinding> find(DiscordRoute route) {
        return storage.query(
                "SELECT webhook_id, token_fingerprint, updated_at FROM " + storage.table(TABLE) + " WHERE route = ?",
                rows -> rows.next()
                        ? Optional.of(new DiscordWebhookBinding(
                                route, rows.getString(1), rows.getString(2), rows.getLong(3)))
                        : Optional.empty(),
                route.name()
        );
    }

    void remember(ManagedDiscordWebhook webhook) {
        long now = System.currentTimeMillis();
        int updated = storage.update(
                "UPDATE " + storage.table(TABLE)
                        + " SET webhook_id = ?, token_fingerprint = ?, updated_at = ? WHERE route = ?",
                webhook.webhookId(), webhook.tokenFingerprint(), now, webhook.route().name()
        );
        if (updated != 0) {
            return;
        }
        try {
            storage.update(
                    "INSERT INTO " + storage.table(TABLE)
                            + " (route, webhook_id, token_fingerprint, updated_at) VALUES (?, ?, ?, ?)",
                    webhook.route().name(), webhook.webhookId(), webhook.tokenFingerprint(), now
            );
        } catch (IllegalStateException race) {
            if (!(race.getCause() instanceof SQLException)) {
                throw race;
            }
            storage.update(
                    "UPDATE " + storage.table(TABLE)
                            + " SET webhook_id = ?, token_fingerprint = ?, updated_at = ? WHERE route = ?",
                    webhook.webhookId(), webhook.tokenFingerprint(), now, webhook.route().name()
            );
        }
    }

    void clear(DiscordRoute route) {
        storage.update("DELETE FROM " + storage.table(TABLE) + " WHERE route = ?", route.name());
    }

    @Override
    public void close() {
        // CoreStorageService is owned by the plugin, not this module repository.
    }
}
