package ua.co.tensa.modules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.co.tensa.Tensa;
import ua.co.tensa.config.Config;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleLifecycleConcurrencyTest {
    @TempDir Path directory;

    @Test
    void fullReloadTargetedReloadAndShutdownNeverOverlapOrRestartAfterShutdown() throws Exception {
        Field registryField = Modules.class.getDeclaredField("REGISTRY");
        registryField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, ModuleEntry> registry = (Map<String, ModuleEntry>) registryField.get(null);
        Field stopping = Modules.class.getDeclaredField("stopping");
        stopping.setAccessible(true);
        var original = new LinkedHashMap<>(registry);
        boolean wasStopping = stopping.getBoolean(null);
        Config oldConfig = Tensa.config;
        Path oldPath = Tensa.pluginPath;
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        AtomicInteger reloads = new AtomicInteger();
        ModuleEntry fake = new ModuleEntry() {
            boolean enabled = true;
            public String id() { return "test-lifecycle"; }
            public String title() { return id(); }
            public boolean required() { return true; }
            public boolean isEnabled() { return enabled; }
            public void enable() { enabled = true; }
            public void disable() { maximum.accumulateAndGet(active.incrementAndGet(), Math::max); enabled = false; active.decrementAndGet(); }
            public void reload() {
                maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
                try {
                    if (reloads.incrementAndGet() == 1) {
                        entered.countDown();
                        if (!release.await(3, TimeUnit.SECONDS)) throw new AssertionError("Test release timed out");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                } finally { active.decrementAndGet(); }
            }
        };
        var workers = Executors.newFixedThreadPool(3);
        try {
            Tensa.pluginPath = directory;
            Tensa.config = new Config();
            registry.clear();
            registry.put(fake.id(), fake);
            stopping.setBoolean(null, false);
            var all = workers.submit(Modules::refresh);
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            CountDownLatch contenders = new CountDownLatch(2);
            var target = workers.submit(() -> { contenders.countDown(); return Modules.reloadModule(fake.id()); });
            var shutdown = workers.submit(() -> { contenders.countDown(); Modules.disableAll(); });
            assertThat(contenders.await(2, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            all.get(3, TimeUnit.SECONDS);
            target.get(3, TimeUnit.SECONDS);
            shutdown.get(3, TimeUnit.SECONDS);
            assertThat(maximum).hasValue(1);
            assertThat(fake.isEnabled()).isFalse();
            int before = reloads.get();
            assertThat(Modules.reloadModule(fake.id())).isEqualTo(Modules.ReloadResult.FAILED);
            assertThat(Modules.refresh()).containsExactly("shutdown-in-progress");
            assertThat(reloads).hasValue(before);
        } finally {
            release.countDown();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            registry.clear();
            registry.putAll(original);
            stopping.setBoolean(null, wasStopping);
            Tensa.config = oldConfig;
            Tensa.pluginPath = oldPath;
        }
    }
}
