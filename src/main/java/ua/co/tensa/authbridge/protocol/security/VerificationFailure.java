package ua.co.tensa.authbridge.protocol.security;

public enum VerificationFailure {
    INVALID_SIGNATURE,
    WRONG_BACKEND,
    INVALID_TIME_WINDOW,
    NOT_YET_VALID,
    EXPIRED,
    REPLAYED_MESSAGE,
    REPLAYED_NONCE,
    STALE_SEQUENCE,
    REPLAY_CAPACITY_EXCEEDED
}
