package ua.co.tensa.authbridge.protocol;

public enum AuthBridgeState {
    LOCKED(0),
    AUTHORIZED(1);

    private final int wireId;

    AuthBridgeState(int wireId) {
        this.wireId = wireId;
    }

    public int wireId() {
        return wireId;
    }

    public static AuthBridgeState fromWireId(int wireId) throws AuthBridgeProtocolException {
        for (AuthBridgeState value : values()) {
            if (value.wireId == wireId) {
                return value;
            }
        }
        throw new AuthBridgeProtocolException("Unknown authentication state: " + wireId);
    }
}
