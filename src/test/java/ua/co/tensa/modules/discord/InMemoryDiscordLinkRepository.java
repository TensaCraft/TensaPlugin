package ua.co.tensa.modules.discord;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class InMemoryDiscordLinkRepository implements DiscordLinkRepository {
    private final Map<UUID, LinkedAccount> byPlayer = new LinkedHashMap<>();
    private final Map<String, UUID> byDiscord = new LinkedHashMap<>();

    @Override
    public void initialize() {
    }

    @Override
    public synchronized Optional<LinkedAccount> findByPlayer(UUID playerUuid) {
        return Optional.ofNullable(byPlayer.get(playerUuid));
    }

    @Override
    public synchronized Optional<LinkedAccount> findByDiscord(String discordUserId) {
        UUID player = byDiscord.get(discordUserId);
        return player == null ? Optional.empty() : Optional.ofNullable(byPlayer.get(player));
    }

    @Override
    public synchronized Collection<LinkedAccount> all() {
        return List.copyOf(byPlayer.values());
    }

    @Override
    public synchronized LinkOutcome link(LinkedAccount account) {
        LinkedAccount existing = byPlayer.get(account.playerUuid());
        if (existing != null) {
            return existing.discordUserId().equals(account.discordUserId())
                    ? LinkOutcome.ALREADY_LINKED
                    : LinkOutcome.PLAYER_LINKED_ELSEWHERE;
        }
        if (byDiscord.containsKey(account.discordUserId())) {
            return LinkOutcome.DISCORD_LINKED_ELSEWHERE;
        }
        byPlayer.put(account.playerUuid(), account);
        byDiscord.put(account.discordUserId(), account.playerUuid());
        return LinkOutcome.LINKED;
    }

    @Override
    public synchronized Optional<LinkedAccount> unlink(UUID playerUuid) throws IOException {
        LinkedAccount removed = byPlayer.remove(playerUuid);
        if (removed != null) {
            byDiscord.remove(removed.discordUserId());
        }
        return Optional.ofNullable(removed);
    }
}
