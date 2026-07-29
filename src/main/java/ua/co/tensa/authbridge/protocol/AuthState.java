package ua.co.tensa.authbridge.protocol;

import java.util.Arrays;

public enum AuthState {
    PENDING(1),
    AWAITING_SECOND_FACTOR(2),
    AUTHORIZED(3),
    REVOKED(4);

    private final int wireId;

    AuthState(int wireId) {
        this.wireId = wireId;
    }

    public int wireId() {
        return wireId;
    }

    public static AuthState fromWireId(int wireId) {
        return Arrays.stream(values())
                .filter(value -> value.wireId == wireId)
                .findFirst()
                .orElseThrow(() -> new AuthProtocolException("Unknown auth state: " + wireId));
    }
}
