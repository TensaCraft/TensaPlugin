package ua.co.tensa.modules.queue;

import ua.co.tensa.core.storage.CoreStorageService;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class JdbcCommandQueueStore implements CommandQueueStore {
    private static final String TABLE = "command_queue_entries";

    private final CoreStorageService storage;
    private final String table;

    JdbcCommandQueueStore(CoreStorageService storage) {
        this.storage = storage;
        this.table = storage.table(TABLE);
    }

    @Override
    public void initialize() {
        storage.createTable(TABLE, """
                id BIGINT PRIMARY KEY,
                target_input VARCHAR(128) NOT NULL,
                target_name VARCHAR(64),
                target_uuid VARCHAR(36),
                command TEXT NOT NULL,
                created_at BIGINT NOT NULL,
                not_before BIGINT NOT NULL,
                created_by VARCHAR(128)
                """);
    }

    @Override
    public List<QueuedCommandEntry> loadAll() {
        return storage.query("SELECT * FROM " + table + " ORDER BY not_before ASC, id ASC", rs -> {
            List<QueuedCommandEntry> entries = new ArrayList<>();
            while (rs.next()) {
                entries.add(entry(rs));
            }
            return entries;
        });
    }

    @Override
    public long nextId() {
        Long max = storage.query("SELECT MAX(id) FROM " + table, rs -> rs.next() ? rs.getLong(1) : 0L);
        return max == null || max < 1L ? 1L : max + 1L;
    }

    @Override
    public void save(QueuedCommandEntry entry) {
        int updated = storage.update("""
                        UPDATE %s SET
                            target_input = ?,
                            target_name = ?,
                            target_uuid = ?,
                            command = ?,
                            created_at = ?,
                            not_before = ?,
                            created_by = ?
                        WHERE id = ?
                        """.formatted(table),
                entry.targetInput(),
                entry.targetName(),
                entry.targetUuid(),
                entry.command(),
                entry.createdAtMillis(),
                entry.notBeforeMillis(),
                entry.createdBy(),
                entry.id()
        );
        if (updated > 0) {
            return;
        }

        storage.update("""
                        INSERT INTO %s (
                            id, target_input, target_name, target_uuid, command, created_at, not_before, created_by
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """.formatted(table),
                entry.id(),
                entry.targetInput(),
                entry.targetName(),
                entry.targetUuid(),
                entry.command(),
                entry.createdAtMillis(),
                entry.notBeforeMillis(),
                entry.createdBy()
        );
    }

    @Override
    public boolean delete(long id) {
        return storage.update("DELETE FROM " + table + " WHERE id = ?", id) > 0;
    }

    @Override
    public int deleteAll(Collection<Long> ids) {
        int deleted = 0;
        for (Long id : ids) {
            if (id != null && delete(id)) {
                deleted++;
            }
        }
        return deleted;
    }

    private QueuedCommandEntry entry(ResultSet rs) throws SQLException {
        return new QueuedCommandEntry(
                rs.getLong("id"),
                safe(rs.getString("target_input")),
                safe(rs.getString("target_name")),
                safe(rs.getString("target_uuid")),
                safe(rs.getString("command")),
                rs.getLong("created_at"),
                rs.getLong("not_before"),
                safe(rs.getString("created_by"))
        );
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
