package ua.co.tensa.modules.authbridge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthBridgeSecretTest {
    @TempDir
    Path pluginDirectory;

    @Test
    void loadsCanonicalBase64Secret() throws Exception {
        byte[] expected = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        Path secret = pluginDirectory.resolve("auth-bridge/secret.key");
        Files.createDirectories(secret.getParent());
        Files.writeString(secret, Base64.getEncoder().encodeToString(expected), StandardCharsets.US_ASCII);

        assertThat(AuthBridgeSecret.load(pluginDirectory, "auth-bridge/secret.key"))
                .isEqualTo(expected);
    }

    @Test
    void rejectsPlaintextShortAndEscapingSecrets() throws Exception {
        Path plain = pluginDirectory.resolve("plain.key");
        Files.writeString(plain, "plain-secret-is-not-base64!", StandardCharsets.US_ASCII);
        Path shortSecret = pluginDirectory.resolve("short.key");
        Files.writeString(
                shortSecret,
                Base64.getEncoder().encodeToString(new byte[16]),
                StandardCharsets.US_ASCII
        );

        assertThatThrownBy(() -> AuthBridgeSecret.load(pluginDirectory, "plain.key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
        assertThatThrownBy(() -> AuthBridgeSecret.load(pluginDirectory, "short.key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
        assertThatThrownBy(() -> AuthBridgeSecret.load(pluginDirectory, "../outside.key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("escapes");
    }
}
