package ua.co.tensa.modules.chat;

/** Boundary between local proxy chat and an optional cross-platform relay. */
@FunctionalInterface
public interface ProxyChatRelay {
    enum Result {
        ACCEPTED,
        LINK_REQUIRED,
        DISABLED,
        BUSY
    }

    Result publish(ProxyChatMessage message);
}
