package ua.co.tensa.authbridge.protocol;

public final class AuthProtocolException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public AuthProtocolException(String message) {
        super(message);
    }

    public AuthProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
