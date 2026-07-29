package ua.co.tensa.authbridge.protocol;

public final class ProtocolConstants {
    public static final String CHANNEL = "tensa:auth";
    public static final int MAGIC = 0x54415042;
    public static final int CURRENT_MAJOR = 1;
    public static final int CURRENT_MINOR = 0;
    public static final int HMAC_BYTES = 32;
    public static final int NONCE_BYTES = 24;
    public static final int CHALLENGE_BYTES = 32;
    public static final int MAX_FRAME_BYTES = 4096;
    public static final int MAX_BACKEND_ID_BYTES = 128;
    public static final int MAX_REASON_BYTES = 512;

    private ProtocolConstants() {
    }
}
