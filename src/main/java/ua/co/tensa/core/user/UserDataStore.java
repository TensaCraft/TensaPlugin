package ua.co.tensa.core.user;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

interface UserDataStore extends AutoCloseable {
    void initialize();

    UserRecordResult recordLogin(UserLoginData data);

    void recordDisconnect(UUID uuid, long timestamp, String server);

    Optional<UserProfile> findByUuid(UUID uuid);

    Optional<UserProfile> findByName(String username);

    Map<String, String> getAllMeta(UUID uuid);

    Optional<String> getMeta(UUID uuid, String key);

    void setMeta(UUID uuid, String key, String value, String valueType);

    void deleteMeta(UUID uuid, String key);

    long getPlayTime(UUID uuid);

    void addPlayTime(UUID uuid, long seconds);

    List<UserProfile> topByPlayTime(int limit);

    @Override
    void close();
}
