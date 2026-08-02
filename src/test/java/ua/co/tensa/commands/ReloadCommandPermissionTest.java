package ua.co.tensa.commands;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReloadCommandPermissionTest {
    @Test
    void modulePermissionAllowsOnlyThatTarget() {
        Set<String> permissions = Set.of("tensa.reload.communications");

        assertThat(ReloadCommand.canReload(permissions::contains, "communications")).isTrue();
        assertThat(ReloadCommand.canReload(permissions::contains, "all")).isFalse();
    }

    @Test
    void broadPermissionAllowsAllForms() {
        Set<String> permissions = Set.of("tensa.reload");

        assertThat(ReloadCommand.canReload(permissions::contains, "communications")).isTrue();
        assertThat(ReloadCommand.canReload(permissions::contains, "all")).isTrue();
    }
}
