package ua.co.tensa.authbridge.protocol;

public final class AuthBridgeProtocolException extends Exception {
    public AuthBridgeProtocolException(String message) {
        super(message);
    }

    public AuthBridgeProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
