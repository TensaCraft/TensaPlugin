package ua.co.tensa.modules.scheduler;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
interface ScheduledCommandDispatcher {
    CompletableFuture<Boolean> dispatch(String command);
}
