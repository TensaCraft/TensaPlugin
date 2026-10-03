package ua.co.tensa.modules.bridge;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import ua.co.tensa.Message;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

final class LegacyProxyBridgeRuntime implements AutoCloseable {
    private final ChannelIdentifier channel;
    private final byte[] token;
    private final Set<String> allowedServers;
    private final boolean log;
    private final Consumer<String> commandExecutor;
    private boolean closed;

    LegacyProxyBridgeRuntime(
            boolean compatibilityMode,
            String channel,
            String token,
            List<String> allowedServers,
            boolean log,
            Consumer<String> commandExecutor
    ) {
        if (!compatibilityMode) {
            throw new IllegalStateException(
                    "ProxyBridge requires compatibility_mode: true in addition to enabling the module"
            );
        }
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("ProxyBridge requires a dedicated non-empty token");
        }
        this.channel = MinecraftChannelIdentifier.from(
                Objects.requireNonNull(channel, "channel").trim()
        );
        this.token = token.getBytes(StandardCharsets.UTF_8);
        this.allowedServers = normalizeAllowlist(allowedServers);
        this.log = log;
        this.commandExecutor = Objects.requireNonNull(commandExecutor, "commandExecutor");
    }

    ChannelIdentifier channel() {
        return channel;
    }

    @Subscribe
    public synchronized void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(channel)) {
            return;
        }

        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (closed) return;
        if (!(event.getSource() instanceof ServerConnection connection)) {
            return;
        }

        String serverName = normalizeServerName(connection.getServerInfo().getName());
        if (!allowedServers.contains(serverName)) {
            if (log) {
                Message.warn(
                        "ProxyBridge compatibility mode blocked a message from disallowed server '"
                                + serverName
                                + "'"
                );
            }
            return;
        }

        String payload = new String(event.getData(), StandardCharsets.UTF_8);
        int separator = payload.indexOf(':');
        if (separator <= 0) {
            if (log) {
                Message.warn("ProxyBridge compatibility mode received an invalid payload");
            }
            return;
        }

        byte[] provided = payload.substring(0, separator).getBytes(StandardCharsets.UTF_8);
        boolean authenticated = MessageDigest.isEqual(token, provided);
        Arrays.fill(provided, (byte) 0);
        if (!authenticated) {
            if (log) {
                Message.warn(
                        "ProxyBridge compatibility mode rejected an invalid token from '"
                                + serverName
                                + "'"
                );
            }
            return;
        }

        String command = payload.substring(separator + 1).trim();
        if (command.isEmpty()) {
            return;
        }
        if (log) {
            Message.info(
                    "ProxyBridge compatibility execution from "
                            + serverName
                            + ": /"
                            + commandLabel(command)
            );
        }
        commandExecutor.accept(command);
    }

    @Override
    public synchronized void close() {
        closed = true;
        Arrays.fill(token, (byte) 0);
    }

    private static Set<String> normalizeAllowlist(List<String> configured) {
        Set<String> normalized = new HashSet<>();
        if (configured != null) {
            for (String server : configured) {
                if (server == null || server.isBlank()) {
                    continue;
                }
                String value = normalizeServerName(server);
                if ("*".equals(value) || "all".equals(value)) {
                    throw new IllegalStateException(
                            "ProxyBridge allow_from does not permit wildcard or all"
                    );
                }
                normalized.add(value);
            }
        }
        if (normalized.isEmpty()) {
            throw new IllegalStateException(
                    "ProxyBridge allow_from must contain at least one exact backend server name"
            );
        }
        return Set.copyOf(normalized);
    }

    private static String normalizeServerName(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String commandLabel(String command) {
        int separator = command.indexOf(' ');
        String label = separator < 0 ? command : command.substring(0, separator);
        return label.length() > 64 ? label.substring(0, 64) : label;
    }
}
