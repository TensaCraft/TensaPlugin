package ua.co.tensa.modules.event;

import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.Scheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.co.tensa.Tensa;
import ua.co.tensa.core.meta.UserMetaService;
import ua.co.tensa.core.user.UserDataService;
import ua.co.tensa.modules.event.data.EventsConfig;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class EventManagerSessionTest {
    @TempDir Path tempDir;

    @Test
    void delayedLoginCallbackDoesNotRepopulateCacheAfterDisconnect() throws Exception {
        withServices((data, meta, tasks) -> {
            UUID uuid = UUID.randomUUID();
            Player player = player(uuid);
            data.setMeta(uuid, "rank", "old");
            EventManager.onPlayerJoin(new PostLoginEvent(player));
            Runnable callback = tasks.poll(2, TimeUnit.SECONDS);
            assertThat(callback).isNotNull();

            EventManager.onPlayerLeave(new DisconnectEvent(player, DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN));
            callback.run();
            data.close();

            assertThat(meta.getCached(uuid)).isEmpty();
            assertThat(data.findByUuid(uuid).orElseThrow().onlineSince()).isZero();
        });
    }

    @Test
    void rejectedDuplicateDisconnectCannotClearTheActivePlayersSession() throws Exception {
        withServices((data, meta, tasks) -> {
            UUID uuid = UUID.randomUUID();
            Player connected = player(uuid);
            EventManager.onPlayerJoin(new PostLoginEvent(connected));
            assertThat(tasks.poll(2, TimeUnit.SECONDS)).isNotNull();
            meta.set(uuid, "session", "active", true);

            EventManager.onPlayerLeave(new DisconnectEvent(player(uuid), DisconnectEvent.LoginStatus.CONFLICTING_LOGIN));
            data.close();

            assertThat(meta.getCached(uuid)).containsEntry("session", "active");
            assertThat(data.findByUuid(uuid).orElseThrow().onlineSince()).isPositive();
        });
    }

    private void withServices(SessionTest test) throws Exception {
        ProxyServer previousServer = Tensa.server;
        UserDataService previousData = Tensa.userData;
        UserMetaService previousMeta = Tensa.userMeta;
        Path previousPath = Tensa.pluginPath;
        BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();
        Tensa.pluginPath = tempDir;
        EventsConfig config = EventsConfig.get();
        boolean previousJoin = config.onJoinEnabled;
        boolean previousFirst = config.onFirstJoinEnabled;
        boolean previousLeave = config.onLeaveEnabled;
        config.onJoinEnabled = false;
        config.onFirstJoinEnabled = false;
        config.onLeaveEnabled = false;
        Scheduler scheduler = (Scheduler) Proxy.newProxyInstance(Scheduler.class.getClassLoader(),
                new Class<?>[]{Scheduler.class}, (proxy, method, args) -> {
                    if (method.getName().equals("buildTask")) {
                        Runnable task = (Runnable) args[1];
                        return Proxy.newProxyInstance(Scheduler.TaskBuilder.class.getClassLoader(),
                                new Class<?>[]{Scheduler.TaskBuilder.class}, (builder, taskMethod, taskArgs) -> {
                                    if (taskMethod.getName().equals("schedule")) {
                                        tasks.add(task);
                                        return null;
                                    }
                                    return builder;
                                });
                    }
                    return null;
                });
        Tensa.server = (ProxyServer) Proxy.newProxyInstance(ProxyServer.class.getClassLoader(),
                new Class<?>[]{ProxyServer.class}, (proxy, method, args) ->
                        method.getName().equals("getScheduler") ? scheduler : null);
        // External storage ownership keeps JDBC reads available after draining the worker.
        try (var storage = ua.co.tensa.core.storage.CoreStorageService.local(tempDir.resolve("users"), "tpl_");
             UserDataService data = UserDataService.createFromStorage(storage);
             UserMetaService meta = new UserMetaService(data, true)) {
            Tensa.userData = data;
            Tensa.userMeta = meta;
            test.run(data, meta, tasks);
        } finally {
            EventManager.shutdown();
            Tensa.server = previousServer;
            Tensa.userData = previousData;
            Tensa.userMeta = previousMeta;
            Tensa.pluginPath = previousPath;
            config.onJoinEnabled = previousJoin;
            config.onFirstJoinEnabled = previousFirst;
            config.onLeaveEnabled = previousLeave;
        }
    }

    private Player player(UUID uuid) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getUsername" -> "Steve";
                    case "getCurrentServer", "getVirtualHost", "getRawVirtualHost" -> Optional.empty();
                    case "isActive" -> true;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> null;
                });
    }

    @FunctionalInterface
    private interface SessionTest {
        void run(UserDataService data, UserMetaService meta, BlockingQueue<Runnable> tasks) throws Exception;
    }
}
