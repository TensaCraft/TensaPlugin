package ua.co.tensa.authbridge.protocol;

public enum AuthBridgeMessageType {
    QUERY(1),
    STATE(2);

    private final int wireId;

    AuthBridgeMessageType(int wireId) {
        this.wireId = wireId;
    }

    public int wireId() {
        return wireId;
    }

    public static AuthBridgeMessageType fromWireId(int wireId) throws AuthBridgeProtocolException {
        for (AuthBridgeMessageType value : values()) {
            if (value.wireId == wireId) {
                return value;
            }
        }
        throw new AuthBridgeProtocolException("Unknown message type: " + wireId);
    }
}
