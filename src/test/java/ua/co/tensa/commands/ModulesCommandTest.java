package ua.co.tensa.commands;

import org.junit.jupiter.api.Test;
import ua.co.tensa.modules.AbstractModule;
import ua.co.tensa.modules.ModuleEntry;

import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class ModulesCommandTest {

    @Test
    void visibleModuleStatusesAreBuiltOnlyFromRegisteredModules() {
        LinkedHashMap<String, ModuleEntry> registry = new LinkedHashMap<>();
        registry.put("proxy-bridge", new TestModule("proxy-bridge", "ProxyBridge"));

        assertThat(ModulesCommand.visibleModuleStatuses(registry))
                .extracting(ModulesCommand.ModuleStatus::id)
                .containsExactly("proxy-bridge");
    }

    private static final class TestModule extends AbstractModule {
        private TestModule(String id, String title) {
            super(id, title);
        }

        @Override
        protected void onEnable() {
        }

        @Override
        protected void onDisable() {
        }
    }
}
