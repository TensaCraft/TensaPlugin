package ua.co.tensa.modules.bridge;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import ua.co.tensa.Message;

public final class ProxyBridgeDebugCommand implements SimpleCommand {
    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!hasPermission(invocation)) {
            Message.send(source, "<red>No permission.</red>");
            return;
        }
        if (!source.equals(ua.co.tensa.Tensa.server.getConsoleCommandSource())) {
            Message.send(source, "<red>Console-only command.</red>");
            return;
        }

        ProxyBridgeModule.Status status = ProxyBridgeModule.status();
        String allowFrom = status.allowFrom().isEmpty()
                ? "<empty>"
                : String.join(", ", status.allowFrom());

        Message.send(source, "<gold>=== ProxyBridge Compatibility ===</gold>");
        Message.send(source, "<gray>Module:</gray> <yellow>" + status.enabled() + "</yellow>");
        Message.send(
                source,
                "<gray>Compatibility mode:</gray> <yellow>"
                        + status.compatibilityMode()
                        + "</yellow>"
        );
        Message.send(
                source,
                "<gray>Channel:</gray> <yellow>"
                        + status.channel()
                        + "</yellow> (<gray>id:</gray> "
                        + MinecraftChannelIdentifier.from(status.channel()).getId()
                        + ")"
        );
        Message.send(
                source,
                "<gray>Dedicated token configured:</gray> <yellow>"
                        + status.tokenConfigured()
                        + "</yellow>"
        );
        Message.send(source, "<gray>allow_from:</gray> <yellow>" + allowFrom + "</yellow>");
        Message.send(source, "<gray>log:</gray> <yellow>" + status.log() + "</yellow>");
        Message.send(source, "<gold>=================================</gold>");
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("tensa.proxybridge.debug");
    }
}
