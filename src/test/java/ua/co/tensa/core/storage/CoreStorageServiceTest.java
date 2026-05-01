package ua.co.tensa.core.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CoreStorageServiceTest {

    @TempDir
    Path tempDir;

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
