package ua.co.tensa.modules;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TargetedModuleReloadTest {
    @Test
    void repeatedTargetedReloadNeverTouchesOtherModule() {
        CountingModule communications = new CountingModule("communications", true, 0, 12);
        CountingModule authentication = new CountingModule("librelogin-auth-bridge", true, 7, 12);
        Map<String, ModuleEntry> modules = new LinkedHashMap<>();
        modules.put(communications.id(), communications);
        modules.put(authentication.id(), authentication);

        assertThat(Modules.reloadModule(modules, "communications")).isEqualTo(Modules.ReloadResult.RELOADED);
        assertThat(Modules.reloadModule(modules, "communications")).isEqualTo(Modules.ReloadResult.RELOADED);
        assertThat(Modules.reloadModule(modules, "communications")).isEqualTo(Modules.ReloadResult.RELOADED);

        assertThat(communications.reloads.get()).isEqualTo(3);
        assertThat(authentication.reloads.get()).isZero();
        assertThat(authentication.activeSessions.get()).isEqualTo(7);
        assertThat(authentication.connectedPlayers.get()).isEqualTo(12);
    }

    @Test
    void disabledAndUnknownModulesAreReportedWithoutLifecycleChanges() {
        CountingModule disabled = new CountingModule("communications", false, 0, 0);
        Map<String, ModuleEntry> modules = Map.of(disabled.id(), disabled);

        assertThat(Modules.reloadModule(modules, "communications")).isEqualTo(Modules.ReloadResult.DISABLED);
        assertThat(Modules.reloadModule(modules, "missing")).isEqualTo(Modules.ReloadResult.NOT_FOUND);
        assertThat(disabled.reloads.get()).isZero();
    }

    private static final class CountingModule implements ModuleEntry {
        private final String id;
        private final boolean enabled;
        private final AtomicInteger reloads = new AtomicInteger();
        private final AtomicInteger activeSessions;
        private final AtomicInteger connectedPlayers;

        private CountingModule(String id, boolean enabled, int activeSessions, int connectedPlayers) {
            this.id = id;
            this.enabled = enabled;
            this.activeSessions = new AtomicInteger(activeSessions);
            this.connectedPlayers = new AtomicInteger(connectedPlayers);
        }

        @Override public String id() { return id; }
        @Override public String title() { return id; }
        @Override public void enable() { }
        @Override public void disable() { }
        @Override public void reload() {
            reloads.incrementAndGet();
            activeSessions.set(0);
            connectedPlayers.set(0);
        }
        @Override public boolean isEnabled() { return enabled; }
    }
}
