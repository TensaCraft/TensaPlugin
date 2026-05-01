package ua.co.tensa.modules.event;

import org.simpleyaml.configuration.ConfigurationSection;
import org.simpleyaml.configuration.file.YamlFile;
import ua.co.tensa.Message;
import ua.co.tensa.config.model.YamlFileIO;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class FirstJoinRegistry implements AutoCloseable {
    private static final String ROOT = "players";

    private final Path filePath;
    private final YamlFile yamlFile;
    private final Set<UUID> seenPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> firstJoinTimes = new ConcurrentHashMap<>();
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable);
        thread.setName("tensa-first-join");
        thread.setDaemon(true);
        return thread;
    });

    FirstJoinRegistry(Path filePath) {
        this.filePath = filePath;
        this.yamlFile = new YamlFile(filePath.toString());
        reload();
    }

    synchronized void reload() {
        seenPlayers.clear();
        firstJoinTimes.clear();

        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (!yamlFile.exists()) {
                yamlFile.createNewFile(true);
            }
            YamlFileIO.loadWithComments(yamlFile);
            yamlFile.setHeader("Persistent state for events.on_first_join_commands.\n" +
                    "Remove a player entry to allow the first-join event to fire again.");
            ConfigurationSection players = yamlFile.getConfigurationSection(ROOT);
            if (players == null) {
                YamlFileIO.saveValidated(yamlFile);
                return;
            }
            for (String rawUuid : players.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(rawUuid);
                    seenPlayers.add(uuid);
                    String firstSeenAt = players.getString(rawUuid + ".first_seen_at", "");
                    if (!firstSeenAt.isBlank()) {
                        firstJoinTimes.put(uuid, firstSeenAt);
                    }
                } catch (IllegalArgumentException ignored) {
                    Message.warn("Events: ignoring invalid first-join UUID entry '" + rawUuid + "'");
                }
            }
        } catch (Exception e) {
            recoverFromCorruptFile(e);
        }
    }

    MarkResult markFirstJoin(UUID uuid, String username) {
        if (uuid == null) {
            return new MarkResult(false, "");
        }

        String existing = firstJoinTimes.get(uuid);
        if (!seenPlayers.add(uuid)) {
            return new MarkResult(false, existing == null ? "" : existing);
        }

        String firstSeenAt = Instant.now().toString();
        firstJoinTimes.put(uuid, firstSeenAt);
        persistAsync(uuid, username, firstSeenAt);
        return new MarkResult(true, firstSeenAt);
    }

    private void persistAsync(UUID uuid, String username, String firstSeenAt) {
        ioExecutor.execute(() -> {
            synchronized (this) {
                try {
                    YamlFileIO.loadWithComments(yamlFile);
                    yamlFile.setHeader("Persistent state for events.on_first_join_commands.\n" +
                            "Remove a player entry to allow the first-join event to fire again.");
                    String base = ROOT + "." + uuid;
                    yamlFile.set(base + ".name", username == null ? "" : username);
                    yamlFile.set(base + ".first_seen_at", firstSeenAt);
                    YamlFileIO.saveValidated(yamlFile);
                } catch (Exception e) {
                    Message.error("Events: failed to persist first-join state for " + uuid + ": " + e.getMessage());
                }
            }
        });
    }

    private void recoverFromCorruptFile(Exception cause) {
        try {
            File file = filePath.toFile();
            if (file.exists()) {
                File backup = new File(filePath + ".corrupt." + System.currentTimeMillis());
                //noinspection ResultOfMethodCallIgnored
                file.renameTo(backup);
                Message.warn("Events: first-join registry was corrupt and was backed up to " + backup.getName());
            }
            yamlFile.createNewFile(true);
            YamlFileIO.loadWithComments(yamlFile);
            yamlFile.setHeader("Persistent state for events.on_first_join_commands.\n" +
                    "Remove a player entry to allow the first-join event to fire again.");
            YamlFileIO.saveValidated(yamlFile);
        } catch (Exception recoveryError) {
            Message.error("Events: failed to recover first-join registry: " + recoveryError.getMessage());
        }
        Message.warn("Events: failed to load first-join registry: " + cause.getMessage());
    }

    @Override
    public void close() {
        ioExecutor.shutdown();
        try {
            if (!ioExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                ioExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            ioExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    record MarkResult(boolean firstJoin, String firstSeenAt) {
    }
}
