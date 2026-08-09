package ua.co.tensa.modules.discord;

import ua.co.tensa.velocity.VelocityPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Validated optional proxy icon used by Discord presentation, never by bot account settings. */
final class ServerIconAsset {
    static final String FILE_NAME = "server-icon.png";
    static final String ATTACHMENT_URL = "attachment://" + FILE_NAME;
    private static final long MAX_BYTES = 10L * 1024L * 1024L;
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private ServerIconAsset() {
    }

    static Optional<Path> path() {
        Path candidate = VelocityPaths.proxyRoot().resolve(FILE_NAME).toAbsolutePath().normalize();
        try {
            if (!Files.isRegularFile(candidate) || Files.size(candidate) < PNG_SIGNATURE.length
                    || Files.size(candidate) > MAX_BYTES) {
                return Optional.empty();
            }
            byte[] signature = new byte[PNG_SIGNATURE.length];
            try (var input = Files.newInputStream(candidate)) {
                if (input.readNBytes(signature, 0, signature.length) != signature.length
                        || !java.util.Arrays.equals(signature, PNG_SIGNATURE)) {
                    return Optional.empty();
                }
            }
            return Optional.of(candidate);
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }
}
