package ua.co.tensa.modules.rcon.server;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.timeout.IdleStateEvent;
import org.junit.jupiter.api.Test;
import ua.co.tensa.Tensa;

import static org.assertj.core.api.Assertions.assertThat;

class RconHandlerTest {
    @Test
    void pipelineFailuresDoNotLogSensitiveExceptionMessages() {
        java.util.List<String> logs = new java.util.ArrayList<>();
        var console = (com.velocitypowered.api.proxy.ConsoleCommandSource) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{com.velocitypowered.api.proxy.ConsoleCommandSource.class},
                (instance, method, args) -> {
                    if (method.getName().equals("sendMessage")) logs.add(args[0].toString());
                    return null;
                });
        Tensa.server = (com.velocitypowered.api.proxy.ProxyServer) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{com.velocitypowered.api.proxy.ProxyServer.class},
                (instance, method, args) -> method.getName().equals("getConsoleCommandSource") ? console : null);
        EmbeddedChannel channel = new EmbeddedChannel(new RconHandler(null, "test-password"));
        try {
            channel.pipeline().fireExceptionCaught(new java.io.IOException("secret-command-argument"));
            assertThat(logs).isNotEmpty();
            assertThat(String.join("", logs)).contains("IOException").doesNotContain("secret-command-argument");
        } finally {
            channel.finishAndReleaseAll();
            Tensa.server = null;
        }
    }

    @Test
    void readIdleEventClosesAbandonedConnection() {
        EmbeddedChannel channel = new EmbeddedChannel(new RconHandler(null, "test-password"));
        try {
            channel.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);
            assertThat(channel.isActive()).isFalse();
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
