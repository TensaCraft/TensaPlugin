package ua.co.tensa.modules.discord;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class AtomicLinkStore {
    public enum LinkOutcome {
        LINKED,
        ALREADY_LINKED,
        PLAYER_LINKED_ELSEWHERE,
        DISCORD_LINKED_ELSEWHERE
    }

    private static final int FORMAT_VERSION = 1;

    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private Map<UUID, LinkedAccount> byPlayer = Map.of();
    private Map<String, UUID> playerByDiscord = Map.of();

    public AtomicLinkStore(Path pluginDirectory, Path relativePath) {
        Path base = pluginDirectory.toAbsolutePath().normalize();
        Path resolved = base.resolve(relativePath).normalize();
        if (!resolved.startsWith(base)) {
            throw new DiscordConfigurationException("Discord link store must stay inside the plugin directory");
        }
        this.file = resolved;
    }

    public synchronized void load() throws IOException {
        if (!Files.exists(file)) {
            byPlayer = Map.of();
            playerByDiscord = Map.of();
            return;
        }
        StoredData data;
        try {
            data = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), StoredData.class);
        } catch (JsonParseException e) {
            throw new IOException("Discord link store is not valid JSON", e);
        }
        if (data == null || data.version != FORMAT_VERSION || data.links == null) {
            throw new IOException("Discord link store has an unsupported format");
        }

        LinkedHashMap<UUID, LinkedAccount> nextByPlayer = new LinkedHashMap<>();
        LinkedHashMap<String, UUID> nextByDiscord = new LinkedHashMap<>();
        for (StoredLink stored : data.links) {
            LinkedAccount account = stored.toAccount();
            if (nextByPlayer.putIfAbsent(account.playerUuid(), account) != null
                    || nextByDiscord.putIfAbsent(account.discordUserId(), account.playerUuid()) != null) {
                throw new IOException("Discord link store contains duplicate account bindings");
            }
        }
        byPlayer = Map.copyOf(nextByPlayer);
        playerByDiscord = Map.copyOf(nextByDiscord);
    }

    public synchronized Optional<LinkedAccount> findByPlayer(UUID playerUuid) {
        return Optional.ofNullable(byPlayer.get(playerUuid));
    }

    public synchronized Optional<LinkedAccount> findByDiscord(String discordUserId) {
        UUID playerUuid = playerByDiscord.get(discordUserId);
        return playerUuid == null ? Optional.empty() : Optional.ofNullable(byPlayer.get(playerUuid));
    }

    public synchronized Collection<LinkedAccount> all() {
        return List.copyOf(byPlayer.values());
    }

    public synchronized LinkOutcome link(LinkedAccount account) throws IOException {
        LinkedAccount playerLink = byPlayer.get(account.playerUuid());
        if (playerLink != null) {
            return playerLink.discordUserId().equals(account.discordUserId())
                    ? LinkOutcome.ALREADY_LINKED
                    : LinkOutcome.PLAYER_LINKED_ELSEWHERE;
        }
        UUID discordLink = playerByDiscord.get(account.discordUserId());
        if (discordLink != null && !discordLink.equals(account.playerUuid())) {
            return LinkOutcome.DISCORD_LINKED_ELSEWHERE;
        }

        LinkedHashMap<UUID, LinkedAccount> next = new LinkedHashMap<>(byPlayer);
        next.put(account.playerUuid(), account);
        persist(next.values());
        replaceState(next);
        return LinkOutcome.LINKED;
    }

    public synchronized Optional<LinkedAccount> unlink(UUID playerUuid) throws IOException {
        LinkedAccount removed = byPlayer.get(playerUuid);
        if (removed == null) {
            return Optional.empty();
        }
        LinkedHashMap<UUID, LinkedAccount> next = new LinkedHashMap<>(byPlayer);
        next.remove(playerUuid);
        persist(next.values());
        replaceState(next);
        return Optional.of(removed);
    }

    private void replaceState(Map<UUID, LinkedAccount> next) {
        LinkedHashMap<String, UUID> nextDiscord = new LinkedHashMap<>();
        next.values().forEach(account -> nextDiscord.put(account.discordUserId(), account.playerUuid()));
        byPlayer = Map.copyOf(next);
        playerByDiscord = Map.copyOf(nextDiscord);
    }

    private void persist(Collection<LinkedAccount> accounts) throws IOException {
        Path parent = file.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
        try {
            StoredData data = new StoredData();
            data.version = FORMAT_VERSION;
            data.links = accounts.stream().map(StoredLink::from).toList();
            byte[] content = gson.toJson(data).getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                channel.write(ByteBuffer.wrap(content));
                channel.force(true);
            }
            restrictPermissions(temporary);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void restrictPermissions(Path path) {
        try {
            Files.setPosixFilePermissions(path, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows ACLs and some filesystems do not expose POSIX permissions.
        }
    }

    private static final class StoredData {
        int version;
        List<StoredLink> links;
    }

    private static final class StoredLink {
        String playerUuid;
        String playerName;
        String discordUserId;
        String discordUserName;
        long linkedAtEpochSecond;

        static StoredLink from(LinkedAccount account) {
            StoredLink stored = new StoredLink();
            stored.playerUuid = account.playerUuid().toString();
            stored.playerName = account.playerName();
            stored.discordUserId = account.discordUserId();
            stored.discordUserName = account.discordUserName();
            stored.linkedAtEpochSecond = account.linkedAt().getEpochSecond();
            return stored;
        }

        LinkedAccount toAccount() throws IOException {
            try {
                if (playerName == null || playerName.isBlank()
                        || discordUserId == null || discordUserId.isBlank()
                        || discordUserName == null) {
                    throw new IllegalArgumentException("missing account field");
                }
                return new LinkedAccount(
                        UUID.fromString(playerUuid),
                        playerName,
                        discordUserId,
                        discordUserName,
                        Instant.ofEpochSecond(linkedAtEpochSecond)
                );
            } catch (RuntimeException e) {
                throw new IOException("Discord link store contains an invalid account", e);
            }
        }
    }
}
