package ua.co.tensa.modules.discord;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

final class DiscordSlashCommandRegistrar {
    static final String COMMAND_MARKER = "TensaPlugin";
    private static final Pattern COMMAND_NAME = Pattern.compile("[a-z0-9_-]{1,32}");

    private final CommandSpec spec;
    private final AtomicReference<CompletableFuture<Result>> inFlight = new AtomicReference<>();

    DiscordSlashCommandRegistrar(String commandName) {
        String normalized = commandName == null ? "" : commandName.trim().toLowerCase(java.util.Locale.ROOT);
        if (!COMMAND_NAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid Discord slash command name");
        }
        this.spec = new CommandSpec(
                normalized,
                "Прив'язати Minecraft-акаунт через " + COMMAND_MARKER
        );
    }

    CommandSpec spec() {
        return spec;
    }

    CompletableFuture<Result> ensureRegistered(GuildCommands guild) {
        Objects.requireNonNull(guild, "guild");
        while (true) {
            CompletableFuture<Result> active = inFlight.get();
            if (active != null) {
                return active;
            }
            CompletableFuture<Result> promise = new CompletableFuture<>();
            if (!inFlight.compareAndSet(null, promise)) {
                continue;
            }
            try {
                register(guild).whenComplete((result, error) -> {
                    if (error == null) {
                        promise.complete(result);
                    } else {
                        promise.completeExceptionally(unwrap(error));
                    }
                    inFlight.compareAndSet(promise, null);
                });
            } catch (RuntimeException error) {
                promise.completeExceptionally(error);
                inFlight.compareAndSet(promise, null);
            }
            return promise;
        }
    }

    private CompletableFuture<Result> register(GuildCommands guild) {
        return guild.list().thenCompose(commands -> {
            List<CommandSnapshot> existing = commands == null ? List.of() : List.copyOf(commands);
            CommandSnapshot nameCollision = existing.stream()
                    .filter(command -> command.name().equals(spec.name()))
                    .filter(command -> !command.ownedByTensa())
                    .findFirst()
                    .orElse(null);
            if (nameCollision != null) {
                return CompletableFuture.failedFuture(new IllegalStateException(
                        "Discord guild command collision: /" + spec.name() + " is not owned by " + COMMAND_MARKER
                ));
            }

            CompletableFuture<Void> cleanup = CompletableFuture.allOf(existing.stream()
                    .filter(CommandSnapshot::ownedByTensa)
                    .filter(command -> !command.name().equals(spec.name()))
                    .map(command -> guild.delete(command.id()))
                    .toArray(CompletableFuture[]::new));

            CommandSnapshot current = existing.stream()
                    .filter(command -> command.name().equals(spec.name()))
                    .findFirst()
                    .orElse(null);
            if (current != null && current.matches(spec)) {
                return cleanup.thenApply(ignored -> new Result(false, current));
            }

            return cleanup.thenCompose(ignored -> guild.upsert(spec))
                    .thenApply(command -> {
                        if (command == null || !command.matches(spec)) {
                            throw new IllegalStateException(
                                    "Discord did not confirm the expected guild command /" + spec.name()
                            );
                        }
                        return new Result(true, command);
                    });
        });
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    interface GuildCommands {
        CompletableFuture<List<CommandSnapshot>> list();

        CompletableFuture<CommandSnapshot> upsert(CommandSpec spec);

        CompletableFuture<Void> delete(String commandId);
    }

    record CommandSpec(String name, String description) {
    }

    record CommandSnapshot(String id, String name, String description, boolean requiredCodeOption) {
        CommandSnapshot {
            id = id == null ? "" : id;
            name = name == null ? "" : name;
            description = description == null ? "" : description;
        }

        boolean ownedByTensa() {
            return description.contains(COMMAND_MARKER);
        }

        boolean matches(CommandSpec expected) {
            return name.equals(expected.name())
                    && description.equals(expected.description())
                    && requiredCodeOption;
        }
    }

    record Result(boolean changed, CommandSnapshot command) {
    }
}
