package ua.co.tensa.modules.discord;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

final class DiscordBotLease implements AutoCloseable {
    private static final ConcurrentHashMap<String, Object> ACTIVE_BOTS = new ConcurrentHashMap<>();

    private final String identity;
    private final Object owner;
    private final AtomicBoolean closed = new AtomicBoolean();

    private DiscordBotLease(String identity, Object owner) {
        this.identity = identity;
        this.owner = owner;
    }

    static DiscordBotLease acquire(String botToken, Object owner) {
        if (botToken == null || botToken.isBlank()) {
            throw new IllegalArgumentException("Discord bot token is required");
        }
        Objects.requireNonNull(owner, "owner");
        String identity = fingerprint(botToken);
        Object existing = ACTIVE_BOTS.putIfAbsent(identity, owner);
        if (existing != null) {
            throw new IllegalStateException(
                    "A Discord runtime for this bot is already active in this plugin process"
            );
        }
        return new DiscordBotLease(identity, owner);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            ACTIVE_BOTS.remove(identity, owner);
        }
    }

    private static String fingerprint(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
