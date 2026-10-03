package ua.co.tensa.config;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLTransientConnectionException;
import java.util.concurrent.atomic.AtomicInteger;
import com.zaxxer.hikari.HikariDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseTest {

    @Test
    void ambiguousWriteFailureIsNeverReplayedOrReplacesTheSharedPool() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger reconnects = new AtomicInteger();
        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class}, (proxy, method, args) -> {
                    if (method.getName().equals("executeUpdate")) {
                        attempts.incrementAndGet(); // Server may already have committed the write.
                        throw new SQLTransientConnectionException("Connection lost before acknowledgement");
                    }
                    return null;
                });
        Connection connection = (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) ->
                        method.getName().equals("prepareStatement") ? statement : null);
        try (HikariDataSource source = new HikariDataSource() {
            @Override public Connection getConnection() { return connection; }
        }) {
            Database database = new Database() {
                @Override public synchronized boolean connect() { reconnects.incrementAndGet(); return true; }
            };
            var field = Database.class.getDeclaredField("dataSource");
            field.setAccessible(true);
            field.set(database, source);
            assertThat(database.update("users", "balance = balance + 1", "id = ?", 1)).isFalse();
            assertThat(attempts).hasValue(1);
            assertThat(reconnects).hasValue(0);
            assertThat(database.getDataSource()).isSameAs(source);
        }
    }

    @Test
    void closedDatabaseCannotReconnectFromLateQueuedWork() {
        AtomicInteger reconnects = new AtomicInteger();
        Database database = new Database() {
            @Override public synchronized boolean connect() { reconnects.incrementAndGet(); return true; }
        };
        database.close();
        assertThat(database.update("users", "balance = 1", "id = ?", 1)).isFalse();
        assertThat(reconnects).hasValue(0);
    }

    @Test
    void castValuesKeepsNumericStringsAsStrings() throws Exception {
        Database database = new Database();
        Method method = Database.class.getDeclaredMethod("castValuesToLong", Object[].class);
        method.setAccessible(true);

        Object[] values = (Object[]) method.invoke(database, (Object) new Object[]{
                "00123",
                "12.5",
                BigInteger.valueOf(42L)
        });

        assertThat(values).containsExactly("00123", "12.5", 42L);
    }
}
