package ua.co.tensa.modules.requests;

import com.sun.net.httpserver.HttpServer;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.Scheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.co.tensa.Tensa;

import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RequestCommandLifecycleTest {
    @TempDir Path temporary;

    @AfterEach
    void cleanup() {
        HttpRequest.shutdown();
        Tensa.server = null;
        Tensa.pluginPath = null;
    }

    @Test
    void completedResponseCannotRunOldCommandsAfterConfigurationReplacement() throws Exception {
        runResponse(true, List.of());
    }

    @Test
    void completedResponseStillRunsCommandsForCurrentConfiguration() throws Exception {
        runResponse(false, List.of("say accepted"));
    }

    private void runResponse(boolean replace, List<String> expected) throws Exception {
        HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        http.createContext("/request", exchange -> {
            exchange.sendResponseHeaders(200, 2);
            exchange.getResponseBody().write(new byte[]{'{', '}'});
            exchange.close();
        });
        http.start();
        try {
            Tensa.pluginPath = temporary;
            Files.createDirectories(temporary.resolve("requests"));
            Files.writeString(temporary.resolve("requests/test.yml"), """
                    triggers: [test]
                    url: "http://127.0.0.1:%d/request"
                    response:
                      success: ["say accepted"]
                    """.formatted(http.getAddress().getPort()));
            RequestsModule.load();
            LinkedBlockingQueue<Runnable> callbacks = new LinkedBlockingQueue<>();
            List<String> executed = new ArrayList<>();
            ConsoleCommandSource source = proxy(ConsoleCommandSource.class, (method, args) -> null);
            CommandManager commands = proxy(CommandManager.class, (method, args) -> {
                if (method.equals("executeAsync")) {
                    executed.add((String) args[1]);
                    return CompletableFuture.completedFuture(true);
                }
                return null;
            });
            Scheduler scheduler = proxy(Scheduler.class, (method, args) -> {
                if (!method.equals("buildTask")) return null;
                Runnable callback = (Runnable) args[1];
                return proxy(Scheduler.TaskBuilder.class, (name, ignored) -> {
                    if (name.equals("schedule")) callbacks.add(callback);
                    return null;
                });
            });
            Tensa.server = proxy(ProxyServer.class, (method, args) -> switch (method) {
                case "getScheduler" -> scheduler;
                case "getCommandManager" -> commands;
                case "getConsoleCommandSource" -> source;
                default -> null;
            });
            SimpleCommand.Invocation invocation = proxy(SimpleCommand.Invocation.class, (method, args) -> switch (method) {
                case "source" -> source;
                case "alias" -> "test";
                case "arguments" -> new String[0];
                default -> null;
            });
            new RequestCommand().execute(invocation);
            Runnable callback = callbacks.poll(3, TimeUnit.SECONDS);
            assertThat(callback).isNotNull();
            if (replace) RequestsModule.load();
            callback.run();
            assertThat(executed).containsExactlyElementsOf(expected);
        } finally {
            http.stop(0);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, Router router) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (instance, method, args) -> router.invoke(method.getName(), args));
    }

    private interface Router {
        Object invoke(String method, Object[] args);
    }
}
