package ua.co.tensa.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.co.tensa.Tensa;
import ua.co.tensa.modules.event.data.EventsConfig;

import java.lang.reflect.Field;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class NoLegacyDirectoryCreationTest {

    @TempDir
    Path tempDir;

    @Test
    void rootConfigsDoNotCreateLegacyEventOrUserMetaDirectories() throws Exception {
        Tensa.pluginPath = tempDir;
        resetEventsConfigSingleton();

        Tensa.config = new Config();
        EventsConfig.get().reloadCfg();

        assertThat(tempDir.resolve("config.yml")).exists();
        assertThat(tempDir.resolve("events.yml")).exists();
        assertThat(tempDir.resolve("events")).doesNotExist();
        assertThat(tempDir.resolve("user_meta")).doesNotExist();
    }

    private void resetEventsConfigSingleton() throws Exception {
        Field instance = EventsConfig.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, null);
    }
}
