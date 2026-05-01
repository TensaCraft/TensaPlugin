package ua.co.tensa.core.meta;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.co.tensa.Tensa;
import ua.co.tensa.config.Config;
import ua.co.tensa.modules.ModuleProvider;

import java.nio.file.Path;
import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

class UserMetaCoreFeatureTest {

    @TempDir
    Path tempDir;

    @Test
    void userMetaIsConfiguredFromRootConfigAndNotDiscoveredAsModule() {
        Tensa.pluginPath = tempDir;
        Tensa.config = new Config();

        boolean discoveredModule = ServiceLoader.load(ModuleProvider.class).stream()
                .map(ServiceLoader.Provider::get)
                .anyMatch(provider -> "user-meta".equals(provider.id()));

        assertThat(discoveredModule).isFalse();
        assertThat(Tensa.config.userMetaDefaultPersist()).isTrue();
        assertThat(Tensa.config.getDatabaseTablePrefix()).isEqualTo("tpl_");
        assertThat(Tensa.config.getModules()).doesNotContain("user-meta");
    }

    @Test
    void configuredDatabasePrefixIsUsedWithoutLegacyFallbacks() throws Exception {
        Tensa.pluginPath = tempDir;
        java.nio.file.Files.writeString(tempDir.resolve("config.yml"), """
                database:
                  table_prefix: custom_
                """);

        Tensa.config = new Config();

        assertThat(Tensa.config.getDatabaseTablePrefix()).isEqualTo("custom_");
    }

    @Test
    void removedUserMetaModuleKeyIsRemovedFromRootConfig() throws Exception {
        Tensa.pluginPath = tempDir;
        java.nio.file.Files.writeString(tempDir.resolve("config.yml"), """
                modules:
                  user-meta: true
                  command-queue: true
                """);

        Tensa.config = new Config();

        assertThat(Tensa.config.getModules()).doesNotContain("user-meta");
        assertThat(Tensa.config.getModules()).contains("command-queue");
    }
}
