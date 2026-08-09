package ua.co.tensa.modules.discord;

import java.io.IOException;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface DiscordLinkRepository {
    enum LinkOutcome {
        LINKED,
        ALREADY_LINKED,
        PLAYER_LINKED_ELSEWHERE,
        DISCORD_LINKED_ELSEWHERE
    }

    void initialize() throws IOException;

    Optional<LinkedAccount> findByPlayer(UUID playerUuid);

    Optional<LinkedAccount> findByDiscord(String discordUserId);

    Collection<LinkedAccount> all();

    default int size() {
        return all().size();
    }

    LinkOutcome link(LinkedAccount account) throws IOException;

    Optional<LinkedAccount> unlink(UUID playerUuid) throws IOException;
}
