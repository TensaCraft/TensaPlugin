package ua.co.tensa.config;

import ua.co.tensa.Message;
import java.math.BigInteger;
import java.sql.*;
import java.util.concurrent.CompletableFuture;

public class Database {

    private volatile Connection connection;
    private String tablePrefix;

    public boolean enabled = false;

    public synchronized boolean connect() {
        String type = Config.getDatabaseType();
        tablePrefix = Config.getDatabaseTablePrefix();

        if ("h2".equalsIgnoreCase(type)) {
            return connectH2();
        } else {
            return connectMySQL();
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                Message.info("Database connection closed successfully.");
            }
        } catch (SQLException e) {
            Message.error("Failed to close database connection: " + e.getMessage());
        }
    }

    public synchronized void checkConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connect();
            }
        } catch (SQLException e) {
            Message.error(e.getMessage());
        }
    }

    private boolean connectH2() {
        try {
            Class.forName("org.h2.Driver");
            String url = "jdbc:h2:file:./plugins/tensa/storage/server;MODE=MySQL;DB_CLOSE_DELAY=-1";
            connection = DriverManager.getConnection(url, "sa", "");
            Message.info("Connection to the H2 database in MySQL mode is successful");
            enabled = true;
            return true;
        } catch (SQLException | ClassNotFoundException e) {
            Message.error("Failed to connect to H2 Database: " + e.getMessage());
            return false;
        }
    }

    private boolean connectMySQL() {
        try {
            Class.forName("org.mariadb.jdbc.Driver");
            String url = "jdbc:mariadb://"
                    + Config.getDatabaseHost() + ":"
                    + Config.getDatabasePort() + "/"
                    + Config.getDatabaseName()
                    + "?useSSL=" + Config.getSsl()
                    + "&autoReconnect=true"            // Allows you to reconnect automatically
                    + "&maxReconnects=3"               // How many times to try
                    + "&connectTimeout=10000"          // Timeout for connection (ms)
                    + "&socketTimeout=20000";          // Timeout for query execution (ms)
            connection = DriverManager.getConnection(
                    url,
                    Config.getDatabaseUser(),
                    Config.getDatabasePassword()
            );
            Message.info("Connection to the MySQL database is successful");
            enabled = true;
            return true;
        } catch (SQLException | ClassNotFoundException e) {
            Message.error("Failed to connect to MySQL: " + e.getMessage());
            return false;
        }
    }

    private boolean executeUpdateSync(String query, Object... parameters) {
        checkConnection();
        return executeSync(query, parameters, PreparedStatement::executeUpdate) != null;
    }

    private ResultSet executeQuerySync(String query, Object... parameters) {
        checkConnection();
        return executeSync(query, parameters, PreparedStatement::executeQuery);
    }

    private <T> T executeSync(String query, Object[] parameters, SQLExecutor<T> executor) {
        checkConnection();
        try (PreparedStatement stmt = prepareStatement(query, parameters)) {
            return executor.execute(stmt);
        } catch (SQLException e) {
            // If the loss of connection - we intelligent and repeat
            if (isConnectionException(e)) {
                Message.info("Lost DB connection — reconnecting and retrying query");
                if (connect()) {
                    try (PreparedStatement stmt2 = prepareStatement(query, parameters)) {
                        return executor.execute(stmt2);
                    } catch (SQLException ex) {
                        Message.error("Retry failed: " + ex.getMessage());
                    }
                } else {
                    Message.error("Reconnect attempt failed");
                }
            } else {
                Message.error(e.getMessage());
            }
        }
        return null;
    }

    private boolean isConnectionException(SQLException e) {
        // We check the classes Exception or the text of the error
        return e instanceof SQLNonTransientConnectionException
                || e instanceof SQLTransientConnectionException
                || e.getMessage().toLowerCase().contains("socket error")
                || e.getMessage().toLowerCase().contains("communicati");
    }


    private PreparedStatement prepareStatement(String query, Object... parameters) throws SQLException {
        checkConnection();
        PreparedStatement preparedStatement = connection.prepareStatement(query);
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i] instanceof String) {
                preparedStatement.setString(i + 1, (String) parameters[i]);
            } else if (parameters[i] instanceof Integer) {
                preparedStatement.setInt(i + 1, (Integer) parameters[i]);
            } else if (parameters[i] instanceof Long) {
                preparedStatement.setLong(i + 1, (Long) parameters[i]);
            } else if (parameters[i] instanceof Double) {
                preparedStatement.setDouble(i + 1, (Double) parameters[i]);
            } else if (parameters[i] instanceof Float) {
                preparedStatement.setFloat(i + 1, (Float) parameters[i]);
            } else if (parameters[i] instanceof Boolean) {
                preparedStatement.setBoolean(i + 1, (Boolean) parameters[i]);
            } else if (parameters[i] instanceof Byte) {
                preparedStatement.setByte(i + 1, (Byte) parameters[i]);
            } else if (parameters[i] instanceof Short) {
                preparedStatement.setShort(i + 1, (Short) parameters[i]);
            } else if (parameters[i] instanceof Date) {
                preparedStatement.setDate(i + 1, (Date) parameters[i]);
            } else if (parameters[i] instanceof Time) {
                preparedStatement.setTime(i + 1, (Time) parameters[i]);
            } else if (parameters[i] instanceof Timestamp) {
                preparedStatement.setTimestamp(i + 1, (Timestamp) parameters[i]);
            } else {
                preparedStatement.setObject(i + 1, parameters[i]);
            }
        }
        return preparedStatement;
    }


    @FunctionalInterface
    private interface SQLExecutor<T> {
        T execute(PreparedStatement preparedStatement) throws SQLException;
    }

    // Helper methods to construct queries
    private String constructInsertQuery(String tableName, String columns, int valueCount) {
        StringBuilder query = new StringBuilder();
        query.append("INSERT INTO ").append(appendPrefix(tableName)).append(" (").append(columns).append(") VALUES (");
        query.append("?,".repeat(Math.max(0, valueCount)));
        query.deleteCharAt(query.length() - 1); // Remove last comma
        query.append(")");
        return query.toString();
    }

    private String constructUpdateOrDeleteQuery(String tableName, String set, String where) {
        return "UPDATE " + appendPrefix(tableName) + " SET " + set + " WHERE " + where;
    }

    private String constructSelectQuery(String tableName, String columns, String where) {
        return "SELECT " + columns + " FROM " + appendPrefix(tableName) + " WHERE " + where;
    }

    // The main methods
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

    public ResultSet select(String tableName, String columns, String where, Object... values) {
        return executeQuerySync(constructSelectQuery(tableName, columns, where), castValuesToLong(values));
    }

    public void createTableAsync(String tableName, String columns) {
        CompletableFuture.supplyAsync(() -> createTable(tableName, columns));
    }

    public void insertAsync(String tableName, String columns, Object... values) {
        CompletableFuture.supplyAsync(() -> insert(tableName, columns, castValuesToLong(values)));
    }

    public void updateAsync(String tableName, String set, String where, Object... values) {
        CompletableFuture.supplyAsync(() -> update(tableName, set, where, castValuesToLong(values)));
    }

    public CompletableFuture<Boolean> deleteAsync(String tableName, String where, Object... values) {
        return CompletableFuture.supplyAsync(() -> delete(tableName, where, castValuesToLong(values)));
    }

    public CompletableFuture<ResultSet> selectAsync(String tableName, String columns, String where, Object... values) {
        return CompletableFuture.supplyAsync(() -> select(tableName, columns, where, castValuesToLong(values)));
    }

    public CompletableFuture<ResultSet> executeQueryAsync(String query, Object... parameters) {
        return CompletableFuture.supplyAsync(() -> executeQuerySync(query, castValuesToLong(parameters)));
    }

    public CompletableFuture<Boolean> executeUpdateAsync(String query, Object... parameters) {
        return CompletableFuture.supplyAsync(() -> executeUpdateSync(query, castValuesToLong(parameters)));
    }

    public boolean exists(String tableName, String where, Object... values) {
        try (ResultSet rs = select(tableName, "*", where, values)) {
            if (rs == null) {
                // Failed to complete the request - we believe that there is no record
                return false;
            }
            return rs.next();
        } catch (SQLException e) {
            Message.error(e.getMessage());
        }
        return false;
    }

    public boolean tableExists(String tableName) {
        checkConnection();
        try {
            DatabaseMetaData dbm = connection.getMetaData();
            try (ResultSet tables = dbm.getTables(null, null, appendPrefix(tableName), null)) {
                return tables.next();
            }
        } catch (SQLException e) {
            Message.error(e.getMessage());
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
