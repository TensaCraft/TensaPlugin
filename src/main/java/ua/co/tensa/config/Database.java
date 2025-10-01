package ua.co.tensa.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;

import java.math.BigInteger;
import java.sql.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Database {

    private volatile HikariDataSource dataSource;
    private String tablePrefix;

    public boolean enabled = false;

    // Dedicated executor for database operations to prevent blocking ForkJoinPool
    private static final ExecutorService DB_EXECUTOR = Executors.newFixedThreadPool(
        Math.max(4, Runtime.getRuntime().availableProcessors()),
        runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("tensa-db-" + thread.getId());
            thread.setDaemon(true);
            return thread;
        }
    );

    public synchronized boolean connect() {
        String type = Tensa.config.getDatabaseType();
        tablePrefix = Tensa.config.getDatabaseTablePrefix();

        return switch (type.toLowerCase()) {
            case "h2" -> connectH2();
            default -> connectMySQL();
        };
    }

    public void close() {
        if (dataSource != null) {
            dataSource.close();
            Message.database("POOL CLOSED", "Connection pool shutdown successfully");
            dataSource = null;
        }
    }

    public static void shutdownExecutor() {
        DB_EXECUTOR.shutdown();
        try {
            if (!DB_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                DB_EXECUTOR.shutdownNow();
                if (!DB_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                    Message.database("EXECUTOR SHUTDOWN FAILED", "Database executor did not terminate");
                }
            }
        } catch (InterruptedException e) {
            DB_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public synchronized void checkConnection() {
        if (dataSource == null || dataSource.isClosed()) {
            connect();
        }
    }

    private boolean connectH2() {
        try {
            String url = "jdbc:h2:file:./plugins/tensa/storage/server;MODE=MySQL;DB_CLOSE_DELAY=-1";
            HikariConfig cfg = new HikariConfig();
            cfg.setJdbcUrl(url);
            cfg.setUsername("sa");
            cfg.setPassword("");
            cfg.setMaximumPoolSize(5);
            cfg.setMinimumIdle(1);
            cfg.setConnectionTimeout(10_000);
            cfg.setIdleTimeout(60_000);
            dataSource = new HikariDataSource(cfg);
            Message.database("CONNECTED", "H2 database (MySQL mode) ready");
            enabled = true;
            return true;
        } catch (Exception e) {
            Message.database("CONNECTION FAILED", "H2 → " + e.getMessage());
            return false;
        }
    }

    private boolean connectMySQL() {
        try {
            // Ensure driver and required internal classes are loaded early
            Class.forName("org.mariadb.jdbc.Driver");
            try {
                Class.forName("org.mariadb.jdbc.message.client.QuitPacket");
                Class.forName("org.mariadb.jdbc.client.impl.StandardClient");
            } catch (ClassNotFoundException ignored) {
                // Older/newer driver versions may differ; safe to ignore
            }
            String url = "jdbc:mariadb://"
                    + Tensa.config.getDatabaseHost() + ":"
                    + Tensa.config.getDatabasePort() + "/"
                    + Tensa.config.getDatabaseName()
                    + "?useSSL=" + Tensa.config.getSsl()
                    + "&autoReconnect=true"
                    + "&maxReconnects=3"
                    + "&connectTimeout=10000"
                    + "&socketTimeout=20000"
                    + "&tcpKeepAlive=true";
            HikariConfig cfg = new HikariConfig();
            cfg.setJdbcUrl(url);
            cfg.setUsername(Tensa.config.getDatabaseUser());
            cfg.setPassword(Tensa.config.getDatabasePassword());
            cfg.setDriverClassName("org.mariadb.jdbc.Driver");
            cfg.setMaximumPoolSize(10);
            cfg.setMinimumIdle(2);
            cfg.setConnectionTimeout(10_000);
            cfg.setPoolName("TensaDBPool");

            // Discover server-side timeouts to tune Hikari and avoid stale connections
            long serverWaitTimeoutSec = 28_800; // sane default (8h)
            try (Connection probe = DriverManager.getConnection(url, Tensa.config.getDatabaseUser(), Tensa.config.getDatabasePassword());
                 Statement st = probe.createStatement()) {
                long waitTimeout = serverWaitTimeoutSec;
                long interactiveTimeout = serverWaitTimeoutSec;
                try (ResultSet rs = st.executeQuery("SHOW VARIABLES LIKE 'wait_timeout'")) {
                    if (rs.next()) {
                        waitTimeout = parseLongSafely(rs.getString(2), serverWaitTimeoutSec);
                    }
                }
                try (ResultSet rs2 = st.executeQuery("SHOW VARIABLES LIKE 'interactive_timeout'")) {
                    if (rs2.next()) {
                        interactiveTimeout = parseLongSafely(rs2.getString(2), serverWaitTimeoutSec);
                    }
                }
                serverWaitTimeoutSec = Math.max(1, Math.min(waitTimeout, interactiveTimeout));
            } catch (SQLException ignored) {
                // If probing fails, fall back to defaults
            }

            // Configure lifetimes relative to server timeout
            long safetyMs = 5_000; // retire slightly before server
            long serverTimeoutMs = serverWaitTimeoutSec * 1_000L;
            long maxLifetimeMs = Math.max(30_000L, serverTimeoutMs - safetyMs);
            // Keepalive ping before server may close idle sockets (half of server timeout, at least 15s)
            long keepAliveMs = Math.max(15_000L, Math.min(maxLifetimeMs - 1_000L, serverTimeoutMs / 2));
            // Idle timeout must be less than maxLifetime and server timeout
            long idleTimeoutMs = Math.max(30_000L, Math.min(maxLifetimeMs - 1_000L, serverTimeoutMs - 20_000L));

            cfg.setMaxLifetime(maxLifetimeMs);
            cfg.setKeepaliveTime(keepAliveMs);
            cfg.setIdleTimeout(idleTimeoutMs);
            cfg.setValidationTimeout(5_000);
            dataSource = new HikariDataSource(cfg);
            Message.database("CONNECTED", "MySQL database ready");
            enabled = true;
            return true;
        } catch (Exception e) {
            Message.database("CONNECTION FAILED", "MySQL → " + e.getMessage());
            return false;
        }
    }

    private long parseLongSafely(String value, long fallback) {
        if (value == null) return fallback;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private boolean executeUpdateSync(String query, Object... parameters) {
        checkConnection();
        Integer updated = executeSync(query, parameters, PreparedStatement::executeUpdate);
        return updated != null;
    }

    private <T> T executeSync(String query, Object[] parameters, SQLExecutor<T> executor) {
        checkConnection();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = prepareStatement(conn, query, parameters)) {
            return executor.execute(stmt);
        } catch (SQLException e) {
            if (isConnectionException(e)) {
                Message.database("RECONNECTING", "Lost connection, attempting to reconnect");
                if (connect()) {
                    try (Connection conn2 = dataSource.getConnection();
                         PreparedStatement stmt2 = prepareStatement(conn2, query, parameters)) {
                        return executor.execute(stmt2);
                    } catch (SQLException ex) {
                        Message.database("RETRY FAILED", ex.getMessage());
                    }
                } else {
                    Message.database("RECONNECT FAILED", "Unable to re-establish connection");
                }
            } else {
                Message.database("QUERY ERROR", e.getMessage());
            }
        }
        return null;
    }

    private <T> T executeQuery(String query, Object[] parameters, ResultSetHandler<T> handler) {
        return executeSync(query, parameters, stmt -> {
            try (ResultSet rs = stmt.executeQuery()) {
                return handler.handle(rs);
            }
        });
    }

    private boolean isConnectionException(SQLException e) {
        return e instanceof SQLNonTransientConnectionException
                || e instanceof SQLTransientConnectionException
                || (e.getMessage() != null && (e.getMessage().toLowerCase().contains("socket error")
                || e.getMessage().toLowerCase().contains("communicati")));
    }

    private PreparedStatement prepareStatement(Connection conn, String query, Object... parameters) throws SQLException {
        PreparedStatement preparedStatement = conn.prepareStatement(query);
        for (int i = 0; i < parameters.length; i++) {
            preparedStatement.setObject(i + 1, parameters[i]);
        }
        return preparedStatement;
    }

    @FunctionalInterface
    private interface SQLExecutor<T> {
        T execute(PreparedStatement preparedStatement) throws SQLException;
    }

    @FunctionalInterface
    public interface ResultSetHandler<T> {
        T handle(ResultSet resultSet) throws SQLException;
    }

    private String constructInsertQuery(String tableName, String columns, int valueCount) {
        StringBuilder query = new StringBuilder();
        query.append("INSERT INTO ").append(appendPrefix(tableName)).append(" (").append(columns).append(") VALUES (");
        query.append("?,".repeat(Math.max(0, valueCount)));
        query.deleteCharAt(query.length() - 1);
        query.append(")");
        return query.toString();
    }

    private String constructUpdateOrDeleteQuery(String tableName, String set, String where) {
        return "UPDATE " + appendPrefix(tableName) + " SET " + set + " WHERE " + where;
    }

    private String constructSelectQuery(String tableName, String columns, String where) {
        return "SELECT " + columns + " FROM " + appendPrefix(tableName) + " WHERE " + where;
    }

    public boolean createTable(String tableName, String columns) {
        String query = "CREATE TABLE IF NOT EXISTS " + appendPrefix(tableName) + " (" + columns + ")";
        return executeUpdateSync(query);
    }

    public boolean insert(String tableName, String columns, Object... values) {
        return executeUpdateSync(constructInsertQuery(tableName, columns, values.length), castValuesToLong(values));
    }

    public boolean update(String tableName, String set, String where, Object... values) {
        return executeUpdateSync(constructUpdateOrDeleteQuery(tableName, set, where), castValuesToLong(values));
    }

    public boolean delete(String tableName, String where, Object... values) {
        return executeUpdateSync("DELETE FROM " + appendPrefix(tableName) + " WHERE " + where, castValuesToLong(values));
    }

    public <T> T select(String tableName, String columns, String where, ResultSetHandler<T> handler, Object... values) {
        return executeQuery(constructSelectQuery(tableName, columns, where), castValuesToLong(values), handler);
    }

    public void createTableAsync(String tableName, String columns) {
        CompletableFuture.supplyAsync(() -> createTable(tableName, columns), DB_EXECUTOR);
    }

    public void insertAsync(String tableName, String columns, Object... values) {
        CompletableFuture.supplyAsync(() -> insert(tableName, columns, castValuesToLong(values)), DB_EXECUTOR);
    }

    public void updateAsync(String tableName, String set, String where, Object... values) {
        CompletableFuture.supplyAsync(() -> update(tableName, set, where, castValuesToLong(values)), DB_EXECUTOR);
    }

    public CompletableFuture<Boolean> deleteAsync(String tableName, String where, Object... values) {
        return CompletableFuture.supplyAsync(() -> delete(tableName, where, castValuesToLong(values)), DB_EXECUTOR);
    }

    public <T> CompletableFuture<T> selectAsync(String tableName, String columns, String where, ResultSetHandler<T> handler, Object... values) {
        Object[] params = castValuesToLong(values);
        return CompletableFuture.supplyAsync(() -> executeQuery(constructSelectQuery(tableName, columns, where), params, handler), DB_EXECUTOR);
    }

    public <T> CompletableFuture<T> executeQueryAsync(String query, ResultSetHandler<T> handler, Object... parameters) {
        Object[] params = castValuesToLong(parameters);
        return CompletableFuture.supplyAsync(() -> executeQuery(query, params, handler), DB_EXECUTOR);
    }

    public CompletableFuture<Boolean> executeUpdateAsync(String query, Object... parameters) {
        return CompletableFuture.supplyAsync(() -> executeUpdateSync(query, castValuesToLong(parameters)), DB_EXECUTOR);
    }

    public boolean exists(String tableName, String where, Object... values) {
        Boolean exists = select(tableName, "*", where, rs -> rs != null && rs.next(), values);
        return Boolean.TRUE.equals(exists);
    }

    public boolean tableExists(String tableName) {
        checkConnection();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData dbm = conn.getMetaData();
            try (ResultSet tables = dbm.getTables(null, null, appendPrefix(tableName), null)) {
                return tables.next();
            }
        } catch (SQLException e) {
            Message.database("TABLE CHECK FAILED", e.getMessage());
            return false;
        }
    }

    private String appendPrefix(String tableName) {
        return tablePrefix + tableName;
    }

    private Object[] castValuesToLong(Object... values) {
        Object[] castedValues = new Object[values.length];
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            if (value instanceof BigInteger) {
                castedValues[i] = ((BigInteger) value).longValue();
            } else if (value instanceof String && isNumeric((String) value)) {
                castedValues[i] = new BigInteger((String) value).longValue();
            } else {
                castedValues[i] = value;
            }
        }
        return castedValues;
    }

    private boolean isNumeric(String str) {
        return str.matches("-?\\d+(\\.\\d+)?");
    }
}
