package ua.co.tensa.velocity;

import ua.co.tensa.Message;
import ua.co.tensa.config.Config;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class VelocityLogCleaner {

    private VelocityLogCleaner() {
    }

    public static void cleanOnStartup(Config config) {
        if (config == null || !config.velocityLogCleanupEnable()) {
            return;
        }

        Path logsDirectory = VelocityPaths.logsDirectory();
        if (!Files.isDirectory(logsDirectory)) {
            Message.info("Velocity log cleanup -> logs directory not found: " + logsDirectory);
            return;
        }

        CleanupStats stats = new CleanupStats();

        if (config.velocityLogCleanupLatestLog()) {
            truncateLatestLog(logsDirectory.resolve("latest.log"), stats);
        }

        try (Stream<Path> files = Files.list(logsDirectory)) {
            files.filter(Files::isRegularFile).forEach(path -> cleanFile(path, config, stats));
        } catch (IOException e) {
            Message.warn("Velocity log cleanup failed to read " + logsDirectory + ": " + e.getMessage());
            return;
        }

        if (stats.deleted == 0 && stats.truncated == 0 && stats.failed.isEmpty()) {
            Message.info("Velocity log cleanup -> nothing to remove");
            return;
        }

        Message.info("Velocity log cleanup -> deleted: " + stats.deleted + ", truncated: " + stats.truncated + ", failed: " + stats.failed.size());
        for (String failure : stats.failed) {
            Message.warn("Velocity log cleanup failed -> " + failure);
        }
    }

    private static void cleanFile(Path path, Config config, CleanupStats stats) {
        String fileName = path.getFileName().toString();
        if ("latest.log".equalsIgnoreCase(fileName)) {
            return;
        }

        boolean deleteCompressed = config.velocityLogCleanupCompressedLogs() && fileName.endsWith(".log.gz");
        boolean deleteRotated = config.velocityLogCleanupRotatedLogs() && fileName.endsWith(".log") && !fileName.endsWith(".log.gz");

        if (!deleteCompressed && !deleteRotated) {
            return;
        }

        try {
            if (Files.deleteIfExists(path)) {
                stats.deleted++;
            }
        } catch (IOException e) {
            stats.failed.add(fileName + ": " + e.getMessage());
        }
    }

    private static void truncateLatestLog(Path latestLog, CleanupStats stats) {
        if (!Files.exists(latestLog)) {
            return;
        }

        try (FileChannel ignored = FileChannel.open(latestLog, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            stats.truncated++;
        } catch (IOException e) {
            stats.failed.add(latestLog.getFileName() + ": " + e.getMessage());
        }
    }

    private static final class CleanupStats {
        private int deleted;
        private int truncated;
        private final List<String> failed = new ArrayList<>();
    }
}
