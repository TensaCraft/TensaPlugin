package ua.co.tensa.modules.discord;

import org.spongepowered.configurate.CommentedConfigurationNode;
import ua.co.tensa.config.model.YamlFileIO;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Establishes the clean Communications v2 configuration contract. Legacy
 * values are archived byte-for-byte and are deliberately not imported.
 */
public final class CommunicationsConfigBootstrap {
    public static final int CONFIG_VERSION = 2;

    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss'Z'")
            .withZone(ZoneOffset.UTC);

    private final Clock clock;
    private final BackupCopier backupCopier;

    public CommunicationsConfigBootstrap() {
        this(Clock.systemUTC());
    }

    CommunicationsConfigBootstrap(Clock clock) {
        this(clock, CommunicationsConfigBootstrap::copyAndVerify);
    }

    CommunicationsConfigBootstrap(Clock clock, BackupCopier backupCopier) {
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.backupCopier = java.util.Objects.requireNonNull(backupCopier, "backupCopier");
    }

    public Result prepare(Path pluginDirectory) throws IOException {
        Path root = pluginDirectory.toAbsolutePath().normalize();
        Path discord = root.resolve("discord.yml");
        Path chats = root.resolve("chats.yml");
        Path links = root.resolve("discord").resolve("links.json");

        int discordVersion = version(discord);
        int chatsVersion = version(chats);
        rejectFutureVersion(discord, discordVersion);
        rejectFutureVersion(chats, chatsVersion);

        boolean reset = existingLegacy(discord, discordVersion) || existingLegacy(chats, chatsVersion);
        List<Path> sources = new ArrayList<>();
        if (reset) {
            addIfPresent(sources, discord);
            addIfPresent(sources, chats);
        }
        addIfPresent(sources, links);

        List<String> archived = sources.stream()
                .map(root::relativize)
                .map(Path::toString)
                .map(value -> value.replace('\\', '/'))
                .toList();
        Path backupRoot = null;
        if (!sources.isEmpty()) {
            backupRoot = root.resolve("backups")
                    .resolve("communications-" + BACKUP_STAMP.format(clock.instant()));
            backupAll(root, backupRoot, sources);
        }

        if (reset || !Files.exists(discord)) {
            writeCleanVersion(discord);
        }
        if (reset || !Files.exists(chats)) {
            writeCleanVersion(chats);
        }
        if (Files.exists(links) && backupRoot != null) {
            Files.delete(links);
        }
        return new Result(reset, archived);
    }

    static void copyAndVerify(Path source, Path target) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
        if (!MessageDigest.isEqual(sha256(source), sha256(target))) {
            Files.deleteIfExists(target);
            throw new IOException("Communications backup verification failed for " + source.getFileName());
        }
    }

    private void backupAll(Path root, Path backupRoot, List<Path> sources) throws IOException {
        List<Path> created = new ArrayList<>();
        try {
            for (Path source : sources) {
                Path target = backupRoot.resolve(root.relativize(source));
                backupCopier.copy(source, target);
                created.add(target);
            }
        } catch (IOException failure) {
            for (int i = created.size() - 1; i >= 0; i--) {
                Files.deleteIfExists(created.get(i));
            }
            Files.deleteIfExists(backupRoot.resolve("discord"));
            Files.deleteIfExists(backupRoot);
            throw failure;
        }
    }

    private static int version(Path path) throws IOException {
        if (!Files.exists(path)) {
            return -1;
        }
        CommentedConfigurationNode yaml = YamlFileIO.load(YamlFileIO.loader(path));
        return yaml.node("config_version").getInt(0);
    }

    private static void rejectFutureVersion(Path path, int version) {
        if (version > CONFIG_VERSION) {
            throw new DiscordConfigurationException(
                    path.getFileName() + " uses newer config_version " + version
            );
        }
    }

    private static boolean existingLegacy(Path path, int version) {
        return Files.exists(path) && version < CONFIG_VERSION;
    }

    private static void addIfPresent(List<Path> values, Path path) {
        if (Files.exists(path)) {
            values.add(path);
        }
    }

    private static void writeCleanVersion(Path target) throws IOException {
        Path parent = target.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = Files.createTempFile(parent, target.getFileName().toString(), ".v2.tmp");
        try {
            CommentedConfigurationNode root = CommentedConfigurationNode.root();
            root.node("config_version").set(CONFIG_VERSION);
            YamlFileIO.loader(temp).save(root);
            YamlFileIO.load(YamlFileIO.loader(temp));
            moveIntoPlace(temp, target);
        } catch (IOException | RuntimeException failure) {
            Files.deleteIfExists(temp);
            throw failure;
        }
    }

    private static void moveIntoPlace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static byte[] sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path);
                 DigestInputStream hashing = new DigestInputStream(input, digest)) {
                hashing.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    @FunctionalInterface
    interface BackupCopier {
        void copy(Path source, Path target) throws IOException;
    }

    public record Result(boolean reset, List<String> archivedFiles) {
        public Result {
            archivedFiles = List.copyOf(archivedFiles);
        }
    }
}
