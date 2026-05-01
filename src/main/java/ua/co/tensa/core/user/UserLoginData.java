package ua.co.tensa.core.user;

import java.util.UUID;

public record UserLoginData(
        UUID uuid,
        String username,
        String ip,
        String virtualHost,
        String protocolVersion,
        String server,
        long timestamp
) {
    public static Builder builder(UUID uuid, String username) {
        return new Builder(uuid, username);
    }

    public static final class Builder {
        private final UUID uuid;
        private final String username;
        private String ip = "";
        private String virtualHost = "";
        private String protocolVersion = "";
        private String server = "";
        private long timestamp = System.currentTimeMillis();

        private Builder(UUID uuid, String username) {
            this.uuid = uuid;
            this.username = username;
        }

        public Builder ip(String ip) {
            this.ip = ip == null ? "" : ip;
            return this;
        }

        public Builder virtualHost(String virtualHost) {
            this.virtualHost = virtualHost == null ? "" : virtualHost;
            return this;
        }

        public Builder protocolVersion(String protocolVersion) {
            this.protocolVersion = protocolVersion == null ? "" : protocolVersion;
            return this;
        }

        public Builder server(String server) {
            this.server = server == null ? "" : server;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public UserLoginData build() {
            return new UserLoginData(uuid, username, ip, virtualHost, protocolVersion, server, timestamp);
        }
    }
}
