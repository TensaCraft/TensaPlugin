package ua.co.tensa.authbridge.protocol.security;

public record VerificationResult(boolean accepted, VerificationFailure failure) {
    public static VerificationResult acceptedResult() {
        return new VerificationResult(true, null);
    }

    public static VerificationResult rejected(VerificationFailure failure) {
        return new VerificationResult(false, failure);
    }
}
