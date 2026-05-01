package ua.co.tensa.modules.queue;

record QueuedCommandEntry(
        long id,
        String targetInput,
        String targetName,
        String targetUuid,
        String command,
        long createdAtMillis,
        long notBeforeMillis,
        String createdBy
) {
    String displayTarget() {
        if (targetName != null && !targetName.isBlank()) {
            return targetName;
        }
        if (targetUuid != null && !targetUuid.isBlank()) {
            return targetUuid;
        }
        return targetInput == null ? "" : targetInput;
    }

    long delaySeconds() {
        return Math.max(0L, (notBeforeMillis - createdAtMillis) / 1000L);
    }

    long remainingSeconds(long nowMillis) {
        return Math.max(0L, (notBeforeMillis - nowMillis + 999L) / 1000L);
    }

    boolean isDue(long nowMillis) {
        return nowMillis >= notBeforeMillis;
    }

    String preview() {
        if (command == null) {
            return "";
        }
        return command.length() > 90 ? command.substring(0, 90) + "..." : command;
    }
}
