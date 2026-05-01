package ua.co.tensa.core.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserDataServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void firstJoinMetaAndPlayTimePersistInLocalSqlStorage() throws Exception {
        UUID uuid = UUID.randomUUID();
        Path databaseFile = tempDir.resolve("storage").resolve("tensa-users");

        try (UserDataService service = UserDataService.local(databaseFile, "tpl_")) {
            UserRecordResult first = service.recordLogin(UserLoginData.builder(uuid, "Steve").build());
            UserRecordResult second = service.recordLogin(UserLoginData.builder(uuid, "Steve").build());

            service.setMeta(uuid, "rank", "admin");
            service.setMeta(uuid, "example-module", "state", "ready", "string");
            service.addPlayTime(uuid, 42L);

            assertThat(first.firstJoin()).isTrue();
            assertThat(first.profile().firstSeenAt()).isPositive();
            assertThat(second.firstJoin()).isFalse();
            assertThat(second.profile().joinCount()).isEqualTo(2L);
            assertThat(service.getMeta(uuid, "rank")).contains("admin");
            assertThat(service.getMeta(uuid, "example-module", "state")).contains("ready");
            assertThat(service.getMetaNamespace(uuid, "example-module")).containsEntry("state", "ready");
            assertThat(service.getPlayTime(uuid)).isEqualTo(42L);
        }

        try (UserDataService service = UserDataService.local(databaseFile, "tpl_")) {
            UserRecordResult afterReload = service.recordLogin(UserLoginData.builder(uuid, "Steve").build());

            assertThat(afterReload.firstJoin()).isFalse();
            assertThat(afterReload.profile().joinCount()).isEqualTo(3L);
            assertThat(service.getMeta(uuid, "rank")).contains("admin");
            assertThat(service.getMeta(uuid, "example-module", "state")).contains("ready");
            assertThat(service.getPlayTime(uuid)).isEqualTo(42L);
        }
    }
}
