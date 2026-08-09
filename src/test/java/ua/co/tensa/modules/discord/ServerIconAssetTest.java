package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.co.tensa.Tensa;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ServerIconAssetTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsOnlyBoundedPngFromTheProxyRoot() throws Exception {
        Path pluginDirectory = temporaryDirectory.resolve("plugins/tensa");
        Files.createDirectories(pluginDirectory);
        Tensa.pluginPath = pluginDirectory;
        Path icon = temporaryDirectory.resolve("server-icon.png");

        Files.write(icon, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1});
        assertThat(ServerIconAsset.path()).contains(icon.toAbsolutePath().normalize());

        Files.writeString(icon, "not a png");
        assertThat(ServerIconAsset.path()).isEmpty();
    }
}
