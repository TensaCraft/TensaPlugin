package ua.co.tensa.modules.runtime;

/** Reports whether a failed runtime replacement successfully restored its prior plan. */
public final class RuntimeReplacementException extends IllegalStateException {
    private final boolean previousRuntimeRestored;

    RuntimeReplacementException(String message, Throwable cause, boolean previousRuntimeRestored) {
        super(message, cause);
        this.previousRuntimeRestored = previousRuntimeRestored;
    }

    public boolean previousRuntimeRestored() {
        return previousRuntimeRestored;
    }
}
