package ua.co.tensa.modules;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractModuleTest {

    @Test
    void failedEnableRunsModuleCleanupAndLeavesModuleDisabled() {
        FailingModule module = new FailingModule();

        module.enable();

        assertThat(module.isEnabled()).isFalse();
        assertThat(module.cleanupCalls).isEqualTo(1);
    }

    @Test
    void failedProtectedSoftReloadKeepsActiveRuntime() {
        ProtectedReloadModule module = new ProtectedReloadModule();
        module.enable();

        module.reload();

        assertThat(module.isEnabled()).isTrue();
        assertThat(module.disableCalls).isZero();
        assertThat(module.enableCalls).isEqualTo(1);
    }

    private static final class FailingModule extends AbstractModule {
        private int cleanupCalls;

        private FailingModule() {
            super("test-module", "Test Module");
        }

        @Override
        protected void onEnable() {
            throw new IllegalStateException("boom");
        }

        @Override
        protected void onDisable() {
            cleanupCalls++;
        }
    }

    private static final class ProtectedReloadModule extends AbstractModule {
        private int enableCalls;
        private int disableCalls;

        private ProtectedReloadModule() {
            super("protected-reload", "Protected Reload");
        }

        @Override
        protected void onEnable() {
            enableCalls++;
        }

        @Override
        protected void onDisable() {
            disableCalls++;
        }

        @Override
        protected void onReload() {
            throw new IllegalArgumentException("invalid replacement config");
        }

        @Override
        protected boolean restartOnReloadFailure() {
            return false;
        }
    }
}
