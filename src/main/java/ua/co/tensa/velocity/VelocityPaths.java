package ua.co.tensa.velocity;

import ua.co.tensa.Tensa;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class VelocityPaths {

    private VelocityPaths() {
    }

    public static Path pluginDirectory() {
        return Tensa.pluginPath == null ? null : Tensa.pluginPath.toAbsolutePath().normalize();
    }

    public static Path proxyRoot() {
        Path pluginDirectory = pluginDirectory();
        if (pluginDirectory == null) {
            return Paths.get(".").toAbsolutePath().normalize();
        }

        Path pluginsDirectory = pluginDirectory.getParent();
        if (pluginsDirectory == null) {
            return pluginDirectory;
        }

        Path root = pluginsDirectory.getParent();
        return root == null ? pluginsDirectory : root;
    }

    public static Path velocityToml() {
        return proxyRoot().resolve("velocity.toml");
    }

    public static Path forwardingSecret() {
        return proxyRoot().resolve("forwarding.secret");
    }

    public static Path logsDirectory() {
        return proxyRoot().resolve("logs");
    }
}
