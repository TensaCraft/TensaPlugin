package ua.co.tensa.core.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import ua.co.tensa.config.Database;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

/**
 * Central SQL storage facade for core services and modules.
 * The selected backend is owned by the main plugin config, not by individual modules.
 */
public final class CoreStorageService implements AutoCloseable {
    private final DataSource dataSource;
    private final String tablePrefix;
    private final AutoCloseable closeable;

    private CoreStorageService(DataSource dataSource, String tablePrefix, AutoCloseable closeable) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource must not be null");
        }
        this.dataSource = dataSource;
        this.tablePrefix = tablePrefix == null ? "" : tablePrefix;
        this.closeable = closeable;
    }

    public static CoreStorageService createFromConfig(Database database) {
        String mode = Tensa.config == null ? "auto" : Tensa.config.getStorageType();
        String prefix = Tensa.config == null ? "tpl_" : Tensa.config.getDatabaseTablePrefix();

        if ("database".equalsIgnoreCase(mode)) {
            if (database == null || !database.enabled || database.getDataSource() == null) {
                throw new IllegalStateException("storage.type=database requires an active configured database connection");
            }
            return external(database.getDataSource(), prefix);
        }

        if ("auto".equalsIgnoreCase(mode) && database != null && database.enabled && database.getDataSource() != null) {
            return external(database.getDataSource(), prefix);
        }

        Path localFile = Tensa.pluginPath.resolve(Tensa.config == null ? "storage/tensa-users" : Tensa.config.getStorageLocalFile());
        return local(localFile, prefix);
    }

    public static CoreStorageService local(Path databaseFile, String tablePrefix) {
        HikariDataSource dataSource = localDataSource(databaseFile);
        return new CoreStorageService(dataSource, tablePrefix, dataSource);
    }

    public static CoreStorageService external(DataSource dataSource, String tablePrefix) {
        return new CoreStorageService(dataSource, tablePrefix, null);
    }

    public DataSource dataSource() {
        return dataSource;
    }

    public String tablePrefix() {
        return tablePrefix;
    }

    public String table(String logicalName) {
        String name = validateIdentifier(logicalName);
        return tablePrefix + name;
    }

    public void createTable(String logicalName, String columnsSql) {
        execute("CREATE TABLE IF NOT EXISTS " + table(logicalName) + " (" + columnsSql + ")");
    }

    public void addColumnIfMissing(String logicalTableName, String columnName, String columnSql) {
        String tableName = table(logicalTableName);
        String column = validateIdentifier(columnName);
        if (columnExists(logicalTableName, column)) {
            return;
        }
        if (columnSql == null || columnSql.isBlank() || columnSql.contains(";")) {
            throw new IllegalArgumentException("Column definition must not be blank or contain statement separators");
        }
        execute("ALTER TABLE " + tableName + " ADD COLUMN " + column + " " + columnSql);
    }

    public boolean columnExists(String logicalTableName, String columnName) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String physicalTableName = table(logicalTableName);
            String column = validateIdentifier(columnName);
            try (ResultSet rs = metaData.getColumns(null, null, physicalTableName, column)) {
                if (rs.next()) {
                    return true;
                }
            }
            try (ResultSet rs = metaData.getColumns(null, null, physicalTableName.toUpperCase(Locale.ROOT), column.toUpperCase(Locale.ROOT))) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Core storage column check failed: " + e.getMessage(), e);
        }
    }

    public void execute(String sql) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("Core storage statement failed: " + e.getMessage(), e);
        }
    }

    public int update(String sql, Object... values) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Core storage update failed: " + e.getMessage(), e);
        }
    }

    public <T> T query(String sql, ResultSetHandler<T> handler, Object... values) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            try (ResultSet rs = statement.executeQuery()) {
                return handler.handle(rs);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Core storage query failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception e) {
            Message.database("CORE STORAGE CLOSE FAILED", e.getMessage());
        }
    }

    private static HikariDataSource localDataSource(Path databaseFile) {
        try {
            Path parent = databaseFile.toAbsolutePath().normalize().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to prepare local storage directory: " + e.getMessage(), e);
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:file:" + databaseFile.toAbsolutePath().normalize() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setPoolName("TensaCoreStorageLocal");
        return new HikariDataSource(config);
    }

    private String validateIdentifier(String logicalName) {
        if (logicalName == null || logicalName.isBlank()) {
            throw new IllegalArgumentException("Storage table name must not be blank");
        }
        String normalized = logicalName.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Storage table name contains unsupported characters: " + logicalName);
        }
        return normalized;
    }

    private void bind(PreparedStatement statement, Object... values) throws SQLException {
        for (int i = 0; i < values.length; i++) {
            statement.setObject(i + 1, values[i]);
        }
    }

    @FunctionalInterface
    public interface ResultSetHandler<T> {
        T handle(ResultSet rs) throws SQLException;
    }
}
