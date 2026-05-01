package ua.co.tensa.core.user;

import ua.co.tensa.Message;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class JdbcUserDataStore implements UserDataStore {
    private final DataSource dataSource;
    private final String prefix;
    private final AutoCloseable closeable;

    JdbcUserDataStore(DataSource dataSource, String prefix, AutoCloseable closeable) {
        this.dataSource = dataSource;
        this.prefix = prefix == null ? "" : prefix;
        this.closeable = closeable;
    }

    @Override
    public void initialize() {
        execute("""
                CREATE TABLE IF NOT EXISTS %susers (
                    uuid VARCHAR(36) PRIMARY KEY,
                    username VARCHAR(64) NOT NULL,
                    first_username VARCHAR(64),
                    first_seen_at BIGINT NOT NULL DEFAULT 0,
                    last_seen_at BIGINT NOT NULL DEFAULT 0,
                    last_disconnect_at BIGINT NOT NULL DEFAULT 0,
                    last_ip VARCHAR(96),
                    last_virtual_host VARCHAR(255),
                    last_protocol_version VARCHAR(64),
                    last_server VARCHAR(128),
                    join_count BIGINT NOT NULL DEFAULT 0,
                    total_play_time_seconds BIGINT NOT NULL DEFAULT 0,
                    online_since BIGINT NOT NULL DEFAULT 0,
                    created_at BIGINT NOT NULL DEFAULT 0,
                    updated_at BIGINT NOT NULL DEFAULT 0
                )
                """.formatted(prefix));

        execute("""
                CREATE TABLE IF NOT EXISTS %suser_meta (
                    uuid VARCHAR(36) NOT NULL,
                    meta_key VARCHAR(128) NOT NULL,
                    meta_value TEXT,
                    value_type VARCHAR(32) NOT NULL DEFAULT 'string',
                    updated_at BIGINT NOT NULL DEFAULT 0,
                    PRIMARY KEY (uuid, meta_key)
                )
                """.formatted(prefix));

        execute("""
                CREATE TABLE IF NOT EXISTS %sschema_migrations (
                    migration_id VARCHAR(128) PRIMARY KEY,
                    applied_at BIGINT NOT NULL
                )
                """.formatted(prefix));
    }

    @Override
    public synchronized UserRecordResult recordLogin(UserLoginData data) {
        if (data.uuid() == null) {
            throw new IllegalArgumentException("User UUID is required");
        }
        long now = data.timestamp() <= 0 ? System.currentTimeMillis() : data.timestamp();
        Optional<UserProfile> existing = findByUuid(data.uuid());
        boolean firstJoin = existing.isEmpty();

        if (firstJoin) {
            update("""
                    INSERT INTO %susers (
                        uuid, username, first_username, first_seen_at, last_seen_at, last_disconnect_at,
                        last_ip, last_virtual_host, last_protocol_version, last_server, join_count,
                        total_play_time_seconds, online_since, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, 0, ?, ?, ?, ?, 1, 0, ?, ?, ?)
                    """.formatted(prefix),
                    data.uuid().toString(),
                    safe(data.username()),
                    safe(data.username()),
                    now,
                    now,
                    safe(data.ip()),
                    safe(data.virtualHost()),
                    safe(data.protocolVersion()),
                    safe(data.server()),
                    now,
                    now,
                    now
            );
        } else {
            update("""
                    UPDATE %susers SET
                        username = ?,
                        last_seen_at = ?,
                        last_ip = ?,
                        last_virtual_host = ?,
                        last_protocol_version = ?,
                        last_server = ?,
                        join_count = join_count + 1,
                        online_since = ?,
                        updated_at = ?
                    WHERE uuid = ?
                    """.formatted(prefix),
                    safe(data.username()),
                    now,
                    safe(data.ip()),
                    safe(data.virtualHost()),
                    safe(data.protocolVersion()),
                    safe(data.server()),
                    now,
                    now,
                    data.uuid().toString()
            );
        }

        return new UserRecordResult(firstJoin, findByUuid(data.uuid()).orElseThrow());
    }

    @Override
    public synchronized void recordDisconnect(UUID uuid, long timestamp, String server) {
        if (uuid == null) {
            return;
        }
        Optional<UserProfile> profile = findByUuid(uuid);
        if (profile.isEmpty()) {
            return;
        }

        long now = timestamp <= 0 ? System.currentTimeMillis() : timestamp;
        long onlineSince = profile.get().onlineSince();
        long addSeconds = onlineSince > 0 && now > onlineSince ? (now - onlineSince) / 1000L : 0L;

        update("""
                UPDATE %susers SET
                    total_play_time_seconds = total_play_time_seconds + ?,
                    last_disconnect_at = ?,
                    last_server = ?,
                    online_since = 0,
                    updated_at = ?
                WHERE uuid = ?
                """.formatted(prefix),
                addSeconds,
                now,
                safe(server),
                now,
                uuid.toString()
        );
    }

    @Override
    public Optional<UserProfile> findByUuid(UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        return queryOne("SELECT * FROM " + prefix + "users WHERE uuid = ?", uuid.toString());
    }

    @Override
    public Optional<UserProfile> findByName(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return queryOne("SELECT * FROM " + prefix + "users WHERE LOWER(username) = LOWER(?)", username);
    }

    @Override
    public Map<String, String> getAllMeta(UUID uuid) {
        Map<String, String> values = new LinkedHashMap<>();
        if (uuid == null) {
            return values;
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT meta_key, meta_value FROM " + prefix + "user_meta WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    values.put(rs.getString(1), rs.getString(2));
                }
            }
        } catch (SQLException e) {
            Message.database("USER META LOAD FAILED", e.getMessage());
        }
        return values;
    }

    @Override
    public Optional<String> getMeta(UUID uuid, String key) {
        if (uuid == null || key == null || key.isBlank()) {
            return Optional.empty();
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT meta_value FROM " + prefix + "user_meta WHERE uuid = ? AND meta_key = ?")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, key);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            Message.database("USER META GET FAILED", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public synchronized void setMeta(UUID uuid, String key, String value, String valueType) {
        if (uuid == null || key == null || key.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (getMeta(uuid, key).isPresent()) {
            update("UPDATE " + prefix + "user_meta SET meta_value = ?, value_type = ?, updated_at = ? WHERE uuid = ? AND meta_key = ?",
                    value, safe(valueType, "string"), now, uuid.toString(), key);
        } else {
            update("INSERT INTO " + prefix + "user_meta (uuid, meta_key, meta_value, value_type, updated_at) VALUES (?, ?, ?, ?, ?)",
                    uuid.toString(), key, value, safe(valueType, "string"), now);
        }
    }

    @Override
    public void deleteMeta(UUID uuid, String key) {
        if (uuid == null || key == null || key.isBlank()) {
            return;
        }
        update("DELETE FROM " + prefix + "user_meta WHERE uuid = ? AND meta_key = ?", uuid.toString(), key);
    }

    @Override
    public long getPlayTime(UUID uuid) {
        return findByUuid(uuid).map(UserProfile::totalPlayTimeSeconds).orElse(0L);
    }

    @Override
    public void addPlayTime(UUID uuid, long seconds) {
        if (uuid == null || seconds <= 0) {
            return;
        }
        update("UPDATE " + prefix + "users SET total_play_time_seconds = total_play_time_seconds + ?, updated_at = ? WHERE uuid = ?",
                seconds, System.currentTimeMillis(), uuid.toString());
    }

    @Override
    public List<UserProfile> topByPlayTime(int limit) {
        List<UserProfile> profiles = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM " + prefix + "users WHERE total_play_time_seconds > 0 ORDER BY total_play_time_seconds DESC LIMIT ?")) {
            statement.setInt(1, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    profiles.add(profile(rs));
                }
            }
        } catch (SQLException e) {
            Message.database("USER TOP FAILED", e.getMessage());
        }
        return profiles;
    }

    @Override
    public boolean migrationApplied(String id) {
        if (id == null || id.isBlank()) {
            return true;
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM " + prefix + "schema_migrations WHERE migration_id = ?")) {
            statement.setString(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            Message.database("MIGRATION CHECK FAILED", e.getMessage());
            return false;
        }
    }

    @Override
    public void markMigrationApplied(String id) {
        if (id == null || id.isBlank() || migrationApplied(id)) {
            return;
        }
        update("INSERT INTO " + prefix + "schema_migrations (migration_id, applied_at) VALUES (?, ?)",
                id, System.currentTimeMillis());
    }

    @Override
    public void close() {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception e) {
            Message.database("USER STORE CLOSE FAILED", e.getMessage());
        }
    }

    private Optional<UserProfile> queryOne(String sql, Object... values) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(profile(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            Message.database("USER QUERY FAILED", e.getMessage());
            return Optional.empty();
        }
    }

    private UserProfile profile(ResultSet rs) throws SQLException {
        return new UserProfile(
                UUID.fromString(rs.getString("uuid")),
                safe(rs.getString("username")),
                safe(rs.getString("first_username")),
                rs.getLong("first_seen_at"),
                rs.getLong("last_seen_at"),
                rs.getLong("last_disconnect_at"),
                safe(rs.getString("last_ip")),
                safe(rs.getString("last_virtual_host")),
                safe(rs.getString("last_protocol_version")),
                safe(rs.getString("last_server")),
                rs.getLong("join_count"),
                rs.getLong("total_play_time_seconds"),
                rs.getLong("online_since"),
                rs.getLong("created_at"),
                rs.getLong("updated_at")
        );
    }

    private void execute(String sql) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize user data table: " + e.getMessage(), e);
        }
    }

    private void update(String sql, Object... values) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("User data update failed: " + e.getMessage(), e);
        }
    }

    private void bind(PreparedStatement statement, Object... values) throws SQLException {
        for (int i = 0; i < values.length; i++) {
            statement.setObject(i + 1, values[i]);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
