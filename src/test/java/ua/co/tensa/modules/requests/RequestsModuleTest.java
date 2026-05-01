package ua.co.tensa.modules.requests;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.co.tensa.Tensa;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RequestsModuleTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setPluginPath() {
        Tensa.pluginPath = tempDir;
    }

    @Test
    void createsDefaultLinkAccountOnlyWhenRequestsDirectoryIsEmpty() {
        RequestsModule.load();

        assertThat(tempDir.resolve("requests").resolve("linkaccount.yml")).exists();
    }

    @Test
    void doesNotCreateDefaultLinkAccountWhenRequestsDirectoryAlreadyHasConfigs() throws Exception {
        Path requestsDir = tempDir.resolve("requests");
        Files.createDirectories(requestsDir);
        Files.writeString(requestsDir.resolve("custom.yml"), """
                triggers:
                  - custom
                """, StandardCharsets.UTF_8);

        RequestsModule.load();

        assertThat(requestsDir.resolve("custom.yml")).exists();
        assertThat(requestsDir.resolve("linkaccount.yml")).doesNotExist();
    }
}
