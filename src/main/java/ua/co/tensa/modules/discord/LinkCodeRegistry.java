package ua.co.tensa.modules.discord;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public final class LinkCodeRegistry {
    public enum ConsumeStatus {
        VALID,
        INVALID,
        EXPIRED
    }

    public record PendingLink(UUID playerUuid, String playerName, Instant expiresAt) {
    }

    public record ConsumeResult(ConsumeStatus status, PendingLink pendingLink) {
        static ConsumeResult valid(PendingLink pendingLink) {
            return new ConsumeResult(ConsumeStatus.VALID, pendingLink);
        }

        static ConsumeResult invalid(ConsumeStatus status) {
            return new ConsumeResult(status, null);
        }
    }

    private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();

    private final Duration ttl;
    private final int length;
    private final Supplier<String> generator;
    private final Map<String, PendingLink> byCode = new ConcurrentHashMap<>();
    private final Map<UUID, String> byPlayer = new ConcurrentHashMap<>();

    public LinkCodeRegistry(Duration ttl, int length) {
        this(ttl, length, secureGenerator(length));
    }

    LinkCodeRegistry(Duration ttl, int length, Supplier<String> generator) {
        this.ttl = ttl;
        this.length = length;
        this.generator = generator;
    }

    public synchronized String issue(UUID playerUuid, String playerName, Instant now) {
        String previous = byPlayer.remove(playerUuid);
        if (previous != null) {
            byCode.remove(previous);
        }

        for (int attempt = 0; attempt < 32; attempt++) {
            String code = normalize(generator.get());
            if (code.length() != length) {
                throw new IllegalStateException("Link code generator returned an invalid code length");
            }
            PendingLink pending = new PendingLink(playerUuid, playerName, now.plus(ttl));
            if (byCode.putIfAbsent(code, pending) == null) {
                byPlayer.put(playerUuid, code);
                return code;
            }
        }
        throw new IllegalStateException("Unable to allocate a unique Discord link code");
    }

    public synchronized ConsumeResult consume(String rawCode, Instant now) {
        String code = normalize(rawCode);
        if (code.length() != length) {
            return ConsumeResult.invalid(ConsumeStatus.INVALID);
        }
        AtomicReference<ConsumeResult> result = new AtomicReference<>(ConsumeResult.invalid(ConsumeStatus.INVALID));
        byCode.compute(code, (ignored, pending) -> {
            if (pending == null) {
                return null;
            }
            byPlayer.remove(pending.playerUuid(), code);
            if (!pending.expiresAt().isAfter(now)) {
                result.set(ConsumeResult.invalid(ConsumeStatus.EXPIRED));
            } else {
                result.set(ConsumeResult.valid(pending));
            }
            return null;
        });
        return result.get();
    }

    public synchronized Optional<PendingLink> pendingFor(UUID playerUuid, Instant now) {
        String code = byPlayer.get(playerUuid);
        if (code == null) {
            return Optional.empty();
        }
        PendingLink pending = byCode.get(code);
        if (pending == null || !pending.expiresAt().isAfter(now)) {
            byPlayer.remove(playerUuid, code);
            byCode.remove(code, pending);
            return Optional.empty();
        }
        return Optional.of(pending);
    }

    public synchronized void invalidate(UUID playerUuid) {
        String code = byPlayer.remove(playerUuid);
        if (code != null) {
            byCode.remove(code);
        }
    }

    private static Supplier<String> secureGenerator(int length) {
        SecureRandom random = new SecureRandom();
        return () -> {
            StringBuilder code = new StringBuilder(length);
            for (int index = 0; index < length; index++) {
                code.append(ALPHABET[random.nextInt(ALPHABET.length)]);
            }
            return code.toString();
        };
    }

    private static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }
}
