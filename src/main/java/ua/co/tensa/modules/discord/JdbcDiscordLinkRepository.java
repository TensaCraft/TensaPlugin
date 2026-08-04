package ua.co.tensa.modules.discord;

import ua.co.tensa.core.storage.CoreStorageService;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class JdbcDiscordLinkRepository implements DiscordLinkRepository {
    private static final String LOGICAL_TABLE = "discord_links";

    private final CoreStorageService storage;
    private final String table;
    private final int maximumLinks;
    private Map<UUID, LinkedAccount> byPlayer = Map.of();
    private Map<String, UUID> playerByDiscord = Map.of();

    public JdbcDiscordLinkRepository(CoreStorageService storage, int maximumLinks) {
        this.storage = java.util.Objects.requireNonNull(storage, "storage");
        if (maximumLinks < 1) {
            throw new IllegalArgumentException("maximumLinks must be positive");
        }
        this.maximumLinks = maximumLinks;
        this.table = storage.table(LOGICAL_TABLE);
    }

    @Override
    public synchronized void initialize() throws IOException {
        try {
            storage.createTable(LOGICAL_TABLE, """
                    player_uuid VARCHAR(36) PRIMARY KEY,
                    player_name VARCHAR(16) NOT NULL,
                    discord_user_id VARCHAR(20) NOT NULL UNIQUE,
                    discord_user_name VARCHAR(80) NOT NULL,
                    linked_at BIGINT NOT NULL
                    """);
            refresh();
        } catch (RuntimeException failure) {
            throw storageFailure("initialize", failure);
        }
    }

    @Override
    public synchronized Optional<LinkedAccount> findByPlayer(UUID playerUuid) {
        return Optional.ofNullable(byPlayer.get(playerUuid));
    }

    @Override
    public synchronized Optional<LinkedAccount> findByDiscord(String discordUserId) {
        UUID playerUuid = playerByDiscord.get(discordUserId);
        return playerUuid == null ? Optional.empty() : Optional.ofNullable(byPlayer.get(playerUuid));
    }

    @Override
    public synchronized Collection<LinkedAccount> all() {
        return List.copyOf(byPlayer.values());
    }

    @Override
    public synchronized LinkOutcome link(LinkedAccount account) throws IOException {
        java.util.Objects.requireNonNull(account, "account");
        LinkOutcome existing = classify(account);
        if (existing != null) {
            return existing;
        }
        if (byPlayer.size() >= maximumLinks) {
            throw new IOException("Discord link repository contains too many account bindings");
        }

        try (Connection connection = storage.dataSource().getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO " + table
                            + " (player_uuid, player_name, discord_user_id, discord_user_name, linked_at)"
                            + " VALUES (?, ?, ?, ?, ?)")) {
                statement.setString(1, account.playerUuid().toString());
                statement.setString(2, account.playerName());
                statement.setString(3, account.discordUserId());
                statement.setString(4, account.discordUserName());
                statement.setLong(5, account.linkedAt().getEpochSecond());
                statement.executeUpdate();
                connection.commit();
                addToCache(account);
                connection.setAutoCommit(autoCommit);
                return LinkOutcome.LINKED;
            } catch (SQLException failure) {
                rollback(connection);
                if (isConstraintViolation(failure)) {
                    refresh();
                    LinkOutcome conflict = classify(account);
                    if (conflict != null) {
                        return conflict;
                    }
                }
                throw failure;
            }
        } catch (SQLException | RuntimeException failure) {
            throw storageFailure("link", failure);
        }
    }

    @Override
    public synchronized Optional<LinkedAccount> unlink(UUID playerUuid) throws IOException {
        LinkedAccount account = byPlayer.get(playerUuid);
        if (account == null) {
            return Optional.empty();
        }
        try (Connection connection = storage.dataSource().getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM " + table + " WHERE player_uuid = ?")) {
                statement.setString(1, playerUuid.toString());
                statement.executeUpdate();
                connection.commit();
                removeFromCache(account);
                connection.setAutoCommit(autoCommit);
                return Optional.of(account);
            } catch (SQLException failure) {
                rollback(connection);
                throw failure;
            }
        } catch (SQLException | RuntimeException failure) {
            throw storageFailure("unlink", failure);
        }
    }

    private void refresh() throws IOException {
        try {
            List<LinkedAccount> rows = storage.query(
                    "SELECT player_uuid, player_name, discord_user_id, discord_user_name, linked_at"
                            + " FROM " + table + " ORDER BY linked_at, player_uuid LIMIT ?",
                    this::readRows,
                    maximumLinks + 1
            );
            if (rows.size() > maximumLinks) {
                throw new IOException("Discord link repository contains too many account bindings");
            }
            LinkedHashMap<UUID, LinkedAccount> nextPlayers = new LinkedHashMap<>();
            LinkedHashMap<String, UUID> nextDiscord = new LinkedHashMap<>();
            for (LinkedAccount account : rows) {
                if (nextPlayers.putIfAbsent(account.playerUuid(), account) != null
                        || nextDiscord.putIfAbsent(account.discordUserId(), account.playerUuid()) != null) {
                    throw new IOException("Discord link repository contains duplicate account bindings");
                }
            }
            byPlayer = Map.copyOf(nextPlayers);
            playerByDiscord = Map.copyOf(nextDiscord);
        } catch (RuntimeException failure) {
            throw storageFailure("load", failure);
        }
    }

    private List<LinkedAccount> readRows(ResultSet result) throws SQLException {
        java.util.ArrayList<LinkedAccount> rows = new java.util.ArrayList<>();
        while (result.next()) {
            try {
                rows.add(new LinkedAccount(
                        UUID.fromString(result.getString("player_uuid")),
                        result.getString("player_name"),
                        result.getString("discord_user_id"),
                        result.getString("discord_user_name"),
                        Instant.ofEpochSecond(result.getLong("linked_at"))
                ));
            } catch (RuntimeException invalid) {
                throw new SQLException("Discord link repository contains an invalid account", invalid);
            }
        }
        return rows;
    }

    private LinkOutcome classify(LinkedAccount requested) {
        LinkedAccount player = byPlayer.get(requested.playerUuid());
        if (player != null) {
            return player.discordUserId().equals(requested.discordUserId())
                    ? LinkOutcome.ALREADY_LINKED
                    : LinkOutcome.PLAYER_LINKED_ELSEWHERE;
        }
        UUID discordPlayer = playerByDiscord.get(requested.discordUserId());
        return discordPlayer == null ? null : LinkOutcome.DISCORD_LINKED_ELSEWHERE;
    }

    private void addToCache(LinkedAccount account) {
        LinkedHashMap<UUID, LinkedAccount> nextPlayers = new LinkedHashMap<>(byPlayer);
        LinkedHashMap<String, UUID> nextDiscord = new LinkedHashMap<>(playerByDiscord);
        nextPlayers.put(account.playerUuid(), account);
        nextDiscord.put(account.discordUserId(), account.playerUuid());
        byPlayer = Map.copyOf(nextPlayers);
        playerByDiscord = Map.copyOf(nextDiscord);
    }

    private void removeFromCache(LinkedAccount account) {
        LinkedHashMap<UUID, LinkedAccount> nextPlayers = new LinkedHashMap<>(byPlayer);
        LinkedHashMap<String, UUID> nextDiscord = new LinkedHashMap<>(playerByDiscord);
        nextPlayers.remove(account.playerUuid());
        nextDiscord.remove(account.discordUserId());
        byPlayer = Map.copyOf(nextPlayers);
        playerByDiscord = Map.copyOf(nextDiscord);
    }

    private static void rollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
        }
    }

    private static boolean isConstraintViolation(SQLException failure) {
        String state = failure.getSQLState();
        return failure instanceof java.sql.SQLIntegrityConstraintViolationException
                || (state != null && state.startsWith("23"));
    }

    private static IOException storageFailure(String operation, Throwable failure) {
        if (failure instanceof IOException io) {
            return io;
        }
        return new IOException("Discord link repository could not " + operation, failure);
    }
}
