package ua.co.tensa.modules.queue;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.co.tensa.Tensa;
import ua.co.tensa.core.storage.CoreStorageService;
import ua.co.tensa.modules.queue.data.CommandQueueConfig;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class CommandQueueManagerTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        Tensa.server = null;
        Tensa.pluginPath = null;
        Tensa.pluginContainer = null;
    }

    @Test
    void enqueuePersistsAcrossReloadsInCoreStorageWithoutYamlBackups() throws Exception {
        Tensa.pluginPath = tempDir;
        Tensa.server = fakeServer(List.of(), new ArrayList<>());
        CommandQueueConfig config = newConfig();
        Path databaseFile = tempDir.resolve("storage").resolve("queue");

        try (CoreStorageService storage = CoreStorageService.local(databaseFile, "tensa_");
             CommandQueueManager manager = new CommandQueueManager(config, storage)) {
            QueuedCommandEntry entry = manager.enqueue("Steve", "broadcast queued welcome", 30L, "console");

            assertThat(entry.id()).isEqualTo(1L);
            assertThat(manager.snapshot()).hasSize(1);
        }

        try (CoreStorageService storage = CoreStorageService.local(databaseFile, "tensa_");
             CommandQueueManager manager = new CommandQueueManager(config, storage)) {
            assertThat(manager.snapshot()).hasSize(1);
            QueuedCommandEntry restored = manager.snapshot().getFirst();
            assertThat(restored.displayTarget()).isEqualTo("Steve");
            assertThat(restored.command()).isEqualTo("broadcast queued welcome");
            assertThat(restored.delaySeconds()).isEqualTo(30L);
            assertThat(restored.createdBy()).isEqualTo("console");
        }

        assertThat(tempDir.resolve("queue").resolve("entries.yml")).doesNotExist();
        try (var files = Files.walk(tempDir)) {
            assertThat(files.map(path -> path.getFileName().toString()))
                    .noneMatch(name -> name.startsWith("entries.yml.bak."));
        }
    }

    @Test
    void dispatchDueExecutesCommandOnceTargetIsOnline() throws Exception {
        Tensa.pluginPath = tempDir;
        List<String> executed = new ArrayList<>();
        UUID playerId = UUID.randomUUID();
        Player player = fakePlayer("Steve", playerId);
        Tensa.server = fakeServer(List.of(player), executed);
        CommandQueueConfig config = newConfig();
        config.requireServerConnection = false;

        try (CoreStorageService storage = CoreStorageService.local(tempDir.resolve("storage").resolve("queue"), "tensa_");
             CommandQueueManager manager = new CommandQueueManager(config, storage)) {
            manager.enqueue(playerId.toString(), "say queued {player}", 0L, "console");

            int dispatched = manager.dispatchDue();

            assertThat(dispatched).isEqualTo(1);
            assertThat(executed).containsExactly("say queued Steve");
            assertThat(manager.snapshot()).isEmpty();
        }
    }

    @Test
    void dispatchDueRespectsDelayBeforeExecutingCommand() throws Exception {
        Tensa.pluginPath = tempDir;
        List<String> executed = new ArrayList<>();
        UUID playerId = UUID.randomUUID();
        Player player = fakePlayer("Steve", playerId);
        Tensa.server = fakeServer(List.of(player), executed);
        CommandQueueConfig config = newConfig();
        config.requireServerConnection = false;

        try (CoreStorageService storage = CoreStorageService.local(tempDir.resolve("storage").resolve("queue"), "tensa_");
             CommandQueueManager manager = new CommandQueueManager(config, storage)) {
            manager.enqueue(playerId.toString(), "say delayed {player}", 1L, "console");

            assertThat(manager.dispatchDue()).isZero();
            assertThat(executed).isEmpty();

            Thread.sleep(1_100L);

            assertThat(manager.dispatchDue()).isEqualTo(1);
            assertThat(executed).containsExactly("say delayed Steve");
            assertThat(manager.snapshot()).isEmpty();
        }
    }

    private CommandQueueConfig newConfig() throws Exception {
        Constructor<CommandQueueConfig> constructor = CommandQueueConfig.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        CommandQueueConfig config = constructor.newInstance();
        config.pollIntervalSeconds = 1;
        config.maxEntries = 100;
        config.maxDispatchPerSweep = 10;
        config.requireServerConnection = true;
        config.logDispatch = false;
        return config;
    }

    private ProxyServer fakeServer(List<Player> players, List<String> executedCommands) {
        ConsoleCommandSource console = proxy(ConsoleCommandSource.class, (method, args) -> defaultValue(method.getReturnType()));
        CommandManager commandManager = proxy(CommandManager.class, (method, args) -> {
            if ("executeAsync".equals(method.getName())) {
                executedCommands.add((String) args[1]);
                return CompletableFuture.completedFuture(true);
            }
            return defaultValue(method.getReturnType());
        });

        return proxy(ProxyServer.class, (method, args) -> switch (method.getName()) {
            case "getAllPlayers" -> players;
            case "getPlayer" -> players.stream()
                    .filter(player -> player.getUniqueId().equals(args[0]))
                    .findFirst();
            case "getCommandManager" -> commandManager;
            case "getConsoleCommandSource" -> console;
            default -> defaultValue(method.getReturnType());
        });
    }

    private Player fakePlayer(String username, UUID uuid) {
        return proxy(Player.class, (method, args) -> switch (method.getName()) {
            case "getUsername" -> username;
            case "getUniqueId" -> uuid;
            case "getCurrentServer" -> Optional.empty();
            default -> defaultValue(method.getReturnType());
        });
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, InvocationRouter router) {
        InvocationHandler handler = (Object proxy, Method method, Object[] args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> type.getSimpleName() + "Proxy";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                };
            }
            return router.invoke(method, args == null ? new Object[0] : args);
        };
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private Object defaultValue(Class<?> returnType) {
        if (returnType == Void.TYPE) {
            return null;
        }
        if (returnType == Boolean.TYPE) {
            return false;
        }
        if (returnType == Integer.TYPE) {
            return 0;
        }
        if (returnType == Long.TYPE) {
            return 0L;
        }
        if (returnType == Double.TYPE) {
            return 0D;
        }
        if (returnType == Float.TYPE) {
            return 0F;
        }
        if (returnType == Short.TYPE) {
            return (short) 0;
        }
        if (returnType == Byte.TYPE) {
            return (byte) 0;
        }
        if (returnType == Character.TYPE) {
            return '\0';
        }
        if (returnType == Optional.class) {
            return Optional.empty();
        }
        return null;
    }

    @FunctionalInterface
    private interface InvocationRouter {
        Object invoke(Method method, Object[] args) throws Throwable;
    }
}
