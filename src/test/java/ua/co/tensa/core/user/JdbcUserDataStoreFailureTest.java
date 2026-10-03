package ua.co.tensa.core.user;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcUserDataStoreFailureTest {
    @Test
    void storageOutageIsNotMisreportedAsMissingUserOrEmptyMetadata() {
        DataSource dataSource = (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(),
                new Class<?>[]{DataSource.class}, (proxy, method, args) -> {
                    throw new SQLException("secret-value from SQL parameter", "08001", 90067);
                });
        JdbcUserDataStore store = new JdbcUserDataStore(dataSource, "tpl_", null);
        UUID uuid = UUID.randomUUID();
        List<Runnable> reads = List.of(
                () -> store.findByUuid(uuid),
                () -> store.getAllMeta(uuid),
                () -> store.getMeta(uuid, "rank"),
                () -> store.topByPlayTime(10));

        for (Runnable read : reads) {
            assertThatThrownBy(read::run)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("08001")
                    .hasMessageNotContaining("secret-value");
        }
    }
}
