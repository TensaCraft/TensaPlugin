package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.co.tensa.Tensa;
import ua.co.tensa.core.storage.CoreStorageService;
import ua.co.tensa.modules.chat.ChatCommands;
import ua.co.tensa.modules.chat.ProxyChatService;
import ua.co.tensa.modules.chat.data.ChatConfig;
import ua.co.tensa.modules.discord.data.DiscordConfig;
import ua.co.tensa.modules.runtime.ModuleScheduler;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommunicationsStartupLifecycleTest {
    @TempDir
    Path tempDir;

    @Test
    void bindingStorageFailureDoesNotLeakAnUnownedWebhookClient() throws Exception {
        CoreStorageService previousStorage = Tensa.storage;
        Path previousPath = Tensa.pluginPath;
        DiscordSettings settings = DiscordTestSettings.create(tempDir);
        ChatConfig chats = new ChatConfig();
        chats.reloadCfg();
        DiscordConfig discord = new DiscordConfig();
        discord.reloadCfg();
        ProxyChatService chat = new ProxyChatService(chats.adapter(), discord.adapter(), null);
        ChatCommands commands = new ChatCommands(chats.adapter(), chat);
        try (CoreStorageService storage = CoreStorageService.local(tempDir.resolve("startup"), "test_");
             CoreStorageService faulty = CoreStorageService.external(failWebhookDdl(storage.dataSource()), "test_");
             ModuleScheduler scheduler = new ModuleScheduler("startup-lifecycle-test",
                     new ModuleScheduler.Defaults(4, 1, 4, Duration.ofSeconds(2), 1,
                             Duration.ofMillis(1), Duration.ofMillis(10), 0))) {
            Tensa.storage = faulty;
            var moduleConstructor = CommunicationsModule.class.getDeclaredConstructor();
            moduleConstructor.setAccessible(true);
            CommunicationsModule module = moduleConstructor.newInstance();
            Class<?> activeType = Class.forName(CommunicationsModule.class.getName() + "$ActiveRuntime");
            var activeConstructor = activeType.getDeclaredConstructor(ProxyChatService.class, ChatCommands.class,
                    ModuleScheduler.class);
            activeConstructor.setAccessible(true);
            Object active = activeConstructor.newInstance(chat, commands, scheduler);
            var start = CommunicationsModule.class.getDeclaredMethod("startDiscord", activeType,
                    DiscordSettings.class, AtomicReference.class);
            start.setAccessible(true);
            Set<Thread> clientsBefore = httpClientThreads();

            assertThatThrownBy(() -> start.invoke(module, active, settings, new AtomicReference<DiscordRuntime>()))
                    .hasRootCauseMessage("simulated webhook binding initialization failure");

            // Java 25 HttpClient starts its owned selector at construction. A
            // failed pre-runtime setup must leave no new live transport behind.
            assertThat(httpClientThreads()).isSubsetOf(clientsBefore);
        } finally {
            Tensa.storage = previousStorage;
            Tensa.pluginPath = previousPath;
        }
    }

    private static Set<Thread> httpClientThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(Thread::isAlive)
                .filter(thread -> thread.getName().startsWith("HttpClient-")
                        && thread.getName().endsWith("-SelectorManager"))
                .collect(Collectors.toSet());
    }

    private static DataSource failWebhookDdl(DataSource delegate) {
        return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[]{DataSource.class},
                (ignored, method, args) -> {
                    Object result = invoke(delegate, method, args);
                    if (!(result instanceof Connection connection)) return result;
                    return Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                            (proxy, operation, arguments) -> {
                                Object value = invoke(connection, operation, arguments);
                                if (!operation.getName().equals("createStatement")) return value;
                                Statement statement = (Statement) value;
                                return Proxy.newProxyInstance(Statement.class.getClassLoader(), new Class<?>[]{Statement.class},
                                        (statementProxy, statementMethod, statementArguments) -> {
                                            if (statementMethod.getName().equals("execute")
                                                    && ((String) statementArguments[0]).contains("discord_webhook_bindings")) {
                                                throw new SQLException("simulated webhook binding initialization failure");
                                            }
                                            return invoke(statement, statementMethod, statementArguments);
                                        });
                            });
                });
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException error) {
            throw error.getCause();
        }
    }
}
