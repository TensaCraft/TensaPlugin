package ua.co.tensa.core.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoreStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsUnsafePrefixesBeforeAnySqlIsConstructed() {
        DataSource dataSource = (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(),
                new Class<?>[]{DataSource.class}, (proxy, method, args) -> null);
        for (String prefix : new String[]{"tpl_;DROP TABLE users;--", "schema.", "unsafe space"}) {
            assertThatThrownBy(() -> CoreStorageService.external(dataSource, prefix))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("prefix");
        }
    }

    @Test
    void queryFailuresDoNotExposeJdbcValuesInPublicErrorMessages() {
        DataSource dataSource = (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(),
                new Class<?>[]{DataSource.class}, (proxy, method, args) -> {
                    throw new SQLException("secret-value from SQL parameter", "28000", 1045);
                });
        CoreStorageService storage = CoreStorageService.external(dataSource, "tpl_");

        assertThatThrownBy(() -> storage.query("SELECT ?", rs -> null, "secret-value"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("28000")
                .hasMessageNotContaining("secret-value");
    }

    @Test
    void moduleTablesUseTheConfiguredCoreStorage() throws Exception {
        Path databaseFile = tempDir.resolve("storage").resolve("core");

        try (CoreStorageService storage = CoreStorageService.local(databaseFile, "tpl_")) {
            storage.createTable("sample_module", """
                    id BIGINT PRIMARY KEY,
                    value_text TEXT NOT NULL
                    """);
            storage.addColumnIfMissing("sample_module", "extra_value", "VARCHAR(64)");
            storage.update("INSERT INTO " + storage.table("sample_module") + " (id, value_text) VALUES (?, ?)",
                    1L,
                    "stored through core");
            assertThat(storage.columnExists("sample_module", "extra_value")).isTrue();
        }

        try (CoreStorageService storage = CoreStorageService.local(databaseFile, "tpl_")) {
            String value = storage.query(
                    "SELECT value_text FROM " + storage.table("sample_module") + " WHERE id = ?",
                    rs -> rs.next() ? rs.getString(1) : "",
                    1L
            );

            assertThat(value).isEqualTo("stored through core");
        }
    }
}
