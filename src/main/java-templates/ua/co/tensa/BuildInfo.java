package ua.co.tensa;

/** Build identity filtered in by Maven from the project version and the Git revision. */
public final class BuildInfo {

    public static final String BASE_VERSION = "${project.version}";
    public static final String COMMIT = "${git.commit.id.abbrev}";
    public static final String VERSION = BASE_VERSION + "+" + COMMIT;

    private BuildInfo() {
    }
}
