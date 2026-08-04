package ua.co.tensa.authbridge.protocol.security;

import ua.co.tensa.authbridge.protocol.AuthFrame;

import java.util.Objects;
import java.util.UUID;

public final class AuthFrameVerifier {
    private final String backendId;
    private final AuthSecurityPolicy policy;
    private final HmacSha256Authenticator authenticator;
    private final ReplayWindow replayWindow;

    public AuthFrameVerifier(
            String backendId,
            AuthSecurityPolicy policy,
            HmacSha256Authenticator authenticator,
            ReplayWindow replayWindow
    ) {
        if (backendId == null || backendId.isBlank()) {
            throw new IllegalArgumentException("backendId must not be blank");
        }
        this.backendId = backendId;
        this.policy = Objects.requireNonNull(policy, "policy");
        this.authenticator = Objects.requireNonNull(authenticator, "authenticator");
        this.replayWindow = Objects.requireNonNull(replayWindow, "replayWindow");
    }

    public VerificationResult verify(AuthFrame frame, long nowEpochMillis) {
        return verify(frame, nowEpochMillis, true);
    }

    public VerificationResult verifyChallenge(AuthFrame frame, long nowEpochMillis) {
        return verify(frame, nowEpochMillis, false);
    }

    private VerificationResult verify(
            AuthFrame frame,
            long nowEpochMillis,
            boolean requireIncreasingSequence
    ) {
        Objects.requireNonNull(frame, "frame");
        if (!authenticator.verify(frame)) {
            return VerificationResult.rejected(VerificationFailure.INVALID_SIGNATURE);
        }
        if (!backendId.equals(frame.backendId())) {
            return VerificationResult.rejected(VerificationFailure.WRONG_BACKEND);
        }
        if (frame.sequence() < 0 || frame.expiresAtEpochMillis() <= frame.issuedAtEpochMillis()) {
            return VerificationResult.rejected(VerificationFailure.INVALID_TIME_WINDOW);
        }
        if (frame.issuedAtEpochMillis() - policy.maximumClockSkew().toMillis() > nowEpochMillis) {
            return VerificationResult.rejected(VerificationFailure.NOT_YET_VALID);
        }
        if (frame.expiresAtEpochMillis() <= nowEpochMillis) {
            return VerificationResult.rejected(VerificationFailure.EXPIRED);
        }
        if (frame.expiresAtEpochMillis() - frame.issuedAtEpochMillis()
                > policy.maximumFrameTtl().toMillis()) {
            return VerificationResult.rejected(VerificationFailure.INVALID_TIME_WINDOW);
        }

        return switch (replayWindow.checkAndRecord(
                frame,
                nowEpochMillis,
                requireIncreasingSequence
        )) {
            case ACCEPTED -> VerificationResult.acceptedResult();
            case DUPLICATE_MESSAGE -> VerificationResult.rejected(VerificationFailure.REPLAYED_MESSAGE);
            case DUPLICATE_NONCE -> VerificationResult.rejected(VerificationFailure.REPLAYED_NONCE);
            case STALE_SEQUENCE -> VerificationResult.rejected(VerificationFailure.STALE_SEQUENCE);
            case CAPACITY_EXCEEDED ->
                    VerificationResult.rejected(VerificationFailure.REPLAY_CAPACITY_EXCEEDED);
        };
    }

    public void forgetSession(UUID playerId, UUID sessionId) {
        replayWindow.forgetSession(playerId, sessionId, backendId);
    }
}
