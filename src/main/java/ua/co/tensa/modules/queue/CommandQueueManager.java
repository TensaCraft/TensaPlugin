package ua.co.tensa.modules.queue;

import com.velocitypowered.api.proxy.Player;
import org.simpleyaml.configuration.ConfigurationSection;
import org.simpleyaml.configuration.file.YamlFile;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import ua.co.tensa.Util;
import ua.co.tensa.config.model.YamlFileIO;
import ua.co.tensa.modules.queue.data.CommandQueueConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class CommandQueueManager implements AutoCloseable {
    private static final String ROOT = "entries";

    private final CommandQueueConfig config;
    private final Path filePath;
    private final YamlFile dataFile;
    private final ConcurrentMap<Long, QueuedCommandEntry> entries = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1L);
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable);
        thread.setName("tensa-queue-io");
        thread.setDaemon(true);
        return thread;
    });
    private final Object fileLock = new Object();

    public CommandQueueManager(CommandQueueConfig config, Path dataDirectory) {
        this.config = config;
        this.filePath = dataDirectory.resolve("queue").resolve("entries.yml");
        this.dataFile = new YamlFile(filePath.toString());
        reload();
    }

    public synchronized void reload() {
        entries.clear();
        synchronized (fileLock) {
            try {
                Files.createDirectories(filePath.getParent());
                if (!dataFile.exists()) {
                    dataFile.createNewFile(true);
                }
                YamlFileIO.loadWithComments(dataFile);
                applyHeader();
                ConfigurationSection section = dataFile.getConfigurationSection(ROOT);
                long maxId = 0L;
                if (section != null) {
                    for (String rawId : section.getKeys(false)) {
                        try {
                            long id = Long.parseLong(rawId);
                            QueuedCommandEntry entry = readEntry(rawId);
                            if (entry != null) {
                                entries.put(id, entry);
                                maxId = Math.max(maxId, id);
                            }
                        } catch (NumberFormatException ignored) {
                            Message.warn("Queue: ignoring invalid entry id '" + rawId + "'");
                        }
                    }
                }
                nextId.set(Math.max(maxId + 1L, dataFile.getLong("next_id", maxId + 1L)));
                saveSnapshot();
            } catch (Exception e) {
                Message.error("Queue: failed to load queue data: " + e.getMessage());
            }
        }
    }

    public QueuedCommandEntry enqueue(String targetInput, String command, long delaySeconds, String createdBy) {
        long id = nextId.getAndIncrement();
        long createdAt = System.currentTimeMillis();
        long notBefore = createdAt + Math.max(0L, delaySeconds) * 1000L;
        ResolvedTarget target = resolveTarget(targetInput);
        QueuedCommandEntry entry = new QueuedCommandEntry(
                id,
                safe(targetInput),
                safe(target.name()),
                safe(target.uuidText()),
                safe(command),
                createdAt,
                notBefore,
                safe(createdBy)
        );
        entries.put(id, entry);
        saveSnapshotAsync();
        return entry;
    }

    public List<QueuedCommandEntry> snapshot() {
        return sorted(entries.values());
    }

    public List<QueuedCommandEntry> snapshot(String selector) {
        if (selector == null || selector.isBlank()) {
            return snapshot();
        }
        return sorted(entries.values().stream()
                .filter(entry -> matchesSelector(entry, selector))
                .toList());
    }

    public QueuedCommandEntry get(long id) {
        return entries.get(id);
    }

    public boolean remove(long id) {
        QueuedCommandEntry removed = entries.remove(id);
        if (removed != null) {
            saveSnapshotAsync();
            return true;
        }
        return false;
    }

    public int clear(String selector) {
        List<QueuedCommandEntry> matches = snapshot(selector);
        if (matches.isEmpty()) {
            return 0;
        }
        int removed = 0;
        for (QueuedCommandEntry entry : matches) {
            if (entries.remove(entry.id(), entry)) {
                removed++;
            }
        }
        if (removed > 0) {
            saveSnapshotAsync();
        }
        return removed;
    }

    public DispatchResult dispatchNow(long id) {
        QueuedCommandEntry entry = entries.get(id);
        if (entry == null) {
            return DispatchResult.notFound(id);
        }
        Player player = findOnlinePlayer(entry).orElse(null);
        if (player == null) {
            return DispatchResult.targetOffline(entry);
        }
        if (!entries.remove(id, entry)) {
            return DispatchResult.notFound(id);
        }
        saveSnapshotAsync();
        dispatch(entry, player, "manual");
        return DispatchResult.dispatched(entry, player.getUsername());
    }

    public int dispatchDue() {
        return dispatchDueForPlayer(null);
    }

    public int dispatchDueForPlayer(Player player) {
        long now = System.currentTimeMillis();
        int dispatched = 0;
        int limit = Math.max(1, config.maxDispatchPerSweep);
        boolean changed = false;
        for (QueuedCommandEntry entry : snapshot()) {
            if (dispatched >= limit) {
                break;
            }
            if (!entry.isDue(now)) {
                continue;
            }
            if (player != null && !matchesPlayer(entry, player)) {
                continue;
            }
            Player target = player != null ? player : findOnlinePlayer(entry).orElse(null);
            if (target == null) {
                continue;
            }
            if (!entries.remove(entry.id(), entry)) {
                continue;
            }
            changed = true;
            dispatch(entry, target, "auto");
            dispatched++;
        }
        if (changed) {
            saveSnapshotAsync();
        }
        return dispatched;
    }

    public QueueStats stats() {
        long now = System.currentTimeMillis();
        int due = 0;
        int online = 0;
        for (QueuedCommandEntry entry : entries.values()) {
            if (entry.isDue(now)) {
                due++;
            }
            if (findOnlinePlayer(entry).isPresent()) {
                online++;
            }
        }
        return new QueueStats(entries.size(), due, online, nextId.get());
    }

    public List<String> targetSuggestions() {
        java.util.LinkedHashSet<String> suggestions = new java.util.LinkedHashSet<>();
        for (Player player : Tensa.server.getAllPlayers()) {
            suggestions.add(player.getUsername());
            suggestions.add(player.getUniqueId().toString());
        }
        for (QueuedCommandEntry entry : snapshot()) {
            suggestions.add(entry.displayTarget());
            if (entry.targetUuid() != null && !entry.targetUuid().isBlank()) {
                suggestions.add(entry.targetUuid());
            }
        }
        suggestions.removeIf(value -> value == null || value.isBlank());
        return new ArrayList<>(suggestions);
    }

    public List<String> idSuggestions() {
        return snapshot().stream()
                .map(entry -> Long.toString(entry.id()))
                .toList();
    }

    @Override
    public void close() {
        saveSnapshot();
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

    private void dispatch(QueuedCommandEntry entry, Player player, String trigger) {
        String command = renderCommand(entry, player).trim();
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        if (command.isBlank()) {
            Message.warn("Queue: command #" + entry.id() + " rendered to an empty string and was dropped");
            return;
        }
        Util.executeCommand(command);
        if (config.logDispatch) {
            Message.info("Queue -> dispatched #" + entry.id() + " for " + player.getUsername() + " via " + trigger + ": " + command);
        }
    }

    private String renderCommand(QueuedCommandEntry entry, Player player) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("player", player.getUsername());
        placeholders.put("player_name", player.getUsername());
        placeholders.put("uuid", player.getUniqueId().toString());
        placeholders.put("target", entry.displayTarget());
        placeholders.put("queue_id", Long.toString(entry.id()));
        placeholders.put("delay", Long.toString(entry.delaySeconds()));
        placeholders.put("createdBy", safe(entry.createdBy()));
        placeholders.put("createdAt", Instant.ofEpochMilli(entry.createdAtMillis()).toString());
        placeholders.put("firstDueAt", Instant.ofEpochMilli(entry.notBeforeMillis()).toString());
        placeholders.put("server", player.getCurrentServer()
                .map(connection -> connection.getServerInfo().getName())
                .orElse(""));
        return Message.renderTemplateString(entry.command(), placeholders);
    }

    private Optional<Player> findOnlinePlayer(QueuedCommandEntry entry) {
        UUID uuid = parseUuid(entry.targetUuid());
        if (uuid != null) {
            Optional<Player> byUuid = Tensa.server.getPlayer(uuid).filter(this::isPlayerReady);
            if (byUuid.isPresent()) {
                return byUuid;
            }
        }

        String name = entry.targetName();
        if (name == null || name.isBlank()) {
            name = entry.targetInput();
        }
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }

        for (Player player : Tensa.server.getAllPlayers()) {
            if (player.getUsername().equalsIgnoreCase(name) && isPlayerReady(player)) {
                return Optional.of(player);
            }
        }
        return Optional.empty();
    }

    private boolean isPlayerReady(Player player) {
        return player != null && (!config.requireServerConnection || player.getCurrentServer().isPresent());
    }

    private boolean matchesPlayer(QueuedCommandEntry entry, Player player) {
        if (player == null) {
            return false;
        }
        UUID uuid = parseUuid(entry.targetUuid());
        if (uuid != null) {
            return uuid.equals(player.getUniqueId());
        }
        return player.getUsername().equalsIgnoreCase(entry.displayTarget());
    }

    private boolean matchesSelector(QueuedCommandEntry entry, String selector) {
        UUID selectorUuid = parseUuid(selector);
        if (selectorUuid != null) {
            UUID entryUuid = parseUuid(entry.targetUuid());
            return selectorUuid.equals(entryUuid);
        }
        return entry.displayTarget().equalsIgnoreCase(selector) || safe(entry.targetInput()).equalsIgnoreCase(selector);
    }

    private ResolvedTarget resolveTarget(String input) {
        UUID uuid = parseUuid(input);
        if (uuid != null) {
            Optional<Player> player = Tensa.server.getPlayer(uuid);
            return new ResolvedTarget(uuid.toString(), player.map(Player::getUsername).orElse(""), uuid.toString());
        }

        for (Player player : Tensa.server.getAllPlayers()) {
            if (player.getUsername().equalsIgnoreCase(input)) {
                return new ResolvedTarget(player.getUsername(), player.getUsername(), player.getUniqueId().toString());
            }
        }

        return new ResolvedTarget(safe(input), safe(input), "");
    }

    private QueuedCommandEntry readEntry(String rawId) {
        String base = ROOT + "." + rawId;
        String command = dataFile.getString(base + ".command", "");
        if (command.isBlank()) {
            return null;
        }
        return new QueuedCommandEntry(
                Long.parseLong(rawId),
                dataFile.getString(base + ".target_input", ""),
                dataFile.getString(base + ".target_name", ""),
                dataFile.getString(base + ".target_uuid", ""),
                command,
                dataFile.getLong(base + ".created_at", System.currentTimeMillis()),
                dataFile.getLong(base + ".not_before", System.currentTimeMillis()),
                dataFile.getString(base + ".created_by", "")
        );
    }

    private void saveSnapshotAsync() {
        ioExecutor.execute(this::saveSnapshot);
    }

    private void saveSnapshot() {
        synchronized (fileLock) {
            try {
                YamlFileIO.loadWithComments(dataFile);
                applyHeader();
                dataFile.set("next_id", nextId.get());
                dataFile.set(ROOT, null);
                for (QueuedCommandEntry entry : snapshot()) {
                    String base = ROOT + "." + entry.id();
                    dataFile.set(base + ".target_input", entry.targetInput());
                    dataFile.set(base + ".target_name", entry.targetName());
                    dataFile.set(base + ".target_uuid", entry.targetUuid());
                    dataFile.set(base + ".command", entry.command());
                    dataFile.set(base + ".created_at", entry.createdAtMillis());
                    dataFile.set(base + ".not_before", entry.notBeforeMillis());
                    dataFile.set(base + ".created_by", entry.createdBy());
                }
                YamlFileIO.saveValidated(dataFile);
            } catch (Exception e) {
                Message.error("Queue: failed to save queue data: " + e.getMessage());
            }
        }
    }

    private void applyHeader() {
        dataFile.setHeader("Queued commands for /tqueue.\nCommands stay here until the target player is online and the delay has passed.");
        dataFile.setComment("next_id", "Internal counter for the next queued command id");
    }

    private List<QueuedCommandEntry> sorted(Collection<QueuedCommandEntry> source) {
        List<QueuedCommandEntry> list = new ArrayList<>(source);
        list.sort(Comparator
                .comparingLong(QueuedCommandEntry::notBeforeMillis)
                .thenComparingLong(QueuedCommandEntry::id));
        return list;
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record ResolvedTarget(String input, String name, String uuidText) {
    }

    public record QueueStats(int totalEntries, int dueEntries, int onlineEligibleEntries, long nextId) {
    }

    public record DispatchResult(Status status, QueuedCommandEntry entry, String playerName) {
        public static DispatchResult notFound(long id) {
            return new DispatchResult(Status.NOT_FOUND, new QueuedCommandEntry(id, "", "", "", "", 0L, 0L, ""), "");
        }

        public static DispatchResult targetOffline(QueuedCommandEntry entry) {
            return new DispatchResult(Status.TARGET_OFFLINE, entry, "");
        }

        public static DispatchResult dispatched(QueuedCommandEntry entry, String playerName) {
            return new DispatchResult(Status.DISPATCHED, entry, playerName);
        }

        public enum Status {
            NOT_FOUND,
            TARGET_OFFLINE,
            DISPATCHED
        }
    }
}
