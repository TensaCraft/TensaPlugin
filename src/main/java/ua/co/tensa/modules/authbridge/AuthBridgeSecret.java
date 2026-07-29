package ua.co.tensa.modules.authbridge;

import ua.co.tensa.authbridge.protocol.AuthBridgeProtocol;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

final class AuthBridgeSecret {
    private AuthBridgeSecret() {
    }

    static byte[] load(Path pluginPath, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalStateException("Auth bridge secret_file must not be empty");
        }
        Path root = pluginPath.toAbsolutePath().normalize();
        Path configured = Path.of(relativePath);
        if (configured.isAbsolute()) {
            throw new IllegalStateException("Auth bridge secret_file must be relative to the Tensa data directory");
        }
        Path secretPath = root.resolve(configured).normalize();
        if (!secretPath.startsWith(root)) {
            throw new IllegalStateException("Auth bridge secret_file escapes the Tensa data directory");
        }
        if (!Files.isRegularFile(secretPath)) {
            throw new IllegalStateException("Auth bridge secret file does not exist: " + secretPath);
        }

        try {
            String encoded = Files.readString(secretPath, StandardCharsets.UTF_8).trim();
            byte[] secret = encoded.startsWith("base64:")
                    ? decodeBase64(encoded.substring("base64:".length()).trim())
                    : encoded.getBytes(StandardCharsets.UTF_8);
            if (secret.length < AuthBridgeProtocol.MIN_SECRET_LENGTH) {
                throw new IllegalStateException(
                        "Auth bridge secret must contain at least "
                                + AuthBridgeProtocol.MIN_SECRET_LENGTH
                                + " bytes"
                );
            }
            return secret;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read auth bridge secret file: " + secretPath, e);
        }
    }

    private static byte[] decodeBase64(String value) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Auth bridge secret contains invalid Base64", e);
        }
    }
}
