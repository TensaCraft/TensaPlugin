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
}
