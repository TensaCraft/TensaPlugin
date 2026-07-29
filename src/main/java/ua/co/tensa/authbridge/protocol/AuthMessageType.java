package ua.co.tensa.authbridge.protocol;

import java.util.Arrays;

public enum AuthMessageType {
    CHALLENGE(1),
    AUTH_STATE(2);

    private final int wireId;

    AuthMessageType(int wireId) {
        this.wireId = wireId;
    }

    public int wireId() {
        return wireId;
    }

    public static AuthMessageType fromWireId(int wireId) {
        return Arrays.stream(values())
                .filter(value -> value.wireId == wireId)
                .findFirst()
                .orElseThrow(() -> new AuthProtocolException("Unknown message type: " + wireId));
    }
}
