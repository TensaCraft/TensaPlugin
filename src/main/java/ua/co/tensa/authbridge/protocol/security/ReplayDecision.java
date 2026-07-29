package ua.co.tensa.authbridge.protocol.security;

public enum ReplayDecision {
    ACCEPTED,
    DUPLICATE_MESSAGE,
    DUPLICATE_NONCE,
    STALE_SEQUENCE,
    CAPACITY_EXCEEDED
}
