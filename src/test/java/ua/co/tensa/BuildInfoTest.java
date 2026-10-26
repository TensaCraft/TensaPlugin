package ua.co.tensa;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuildInfoTest {
    @Test
    void versionCarriesTheFilteredSourceRevision() {
        assertThat(BuildInfo.BASE_VERSION).doesNotContain("${").isNotBlank();
        assertThat(BuildInfo.COMMIT).doesNotContain("${").isNotBlank();
        assertThat(BuildInfo.VERSION).isEqualTo(BuildInfo.BASE_VERSION + "+" + BuildInfo.COMMIT);
    }
}
