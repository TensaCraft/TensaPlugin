package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscordSlashCommandRegistrarTest {
    @Test
    void reconnectRegistrationIsIdempotentAndVerifiesTheGuildCommand() {
        FakeGuildCommands guild = new FakeGuildCommands();
        DiscordSlashCommandRegistrar registrar = new DiscordSlashCommandRegistrar("link");

        DiscordSlashCommandRegistrar.Result first = registrar.ensureRegistered(guild).join();
        DiscordSlashCommandRegistrar.Result second = registrar.ensureRegistered(guild).join();

        assertThat(first.changed()).isTrue();
        assertThat(second.changed()).isFalse();
        assertThat(first.command().matches(registrar.spec())).isTrue();
        assertThat(second.command().matches(registrar.spec())).isTrue();
        assertThat(guild.upserts).hasValue(1);
        assertThat(guild.commands).singleElement().satisfies(command ->
                assertThat(command.matches(registrar.spec())).isTrue());
    }

    @Test
    void concurrentReconnectEventsShareOneRegistrationAttempt() {
        FakeGuildCommands guild = new FakeGuildCommands();
        CompletableFuture<List<DiscordSlashCommandRegistrar.CommandSnapshot>> listGate = new CompletableFuture<>();
        guild.listGate = listGate;
        DiscordSlashCommandRegistrar registrar = new DiscordSlashCommandRegistrar("link");

        CompletableFuture<DiscordSlashCommandRegistrar.Result> first = registrar.ensureRegistered(guild);
        CompletableFuture<DiscordSlashCommandRegistrar.Result> second = registrar.ensureRegistered(guild);

        assertThat(second).isSameAs(first);
        listGate.complete(List.of());
        assertThat(first.join().changed()).isTrue();
        assertThat(guild.upserts).hasValue(1);
    }

    @Test
    void refusesToOverwriteAnUnownedCommandWithTheConfiguredName() {
        FakeGuildCommands guild = new FakeGuildCommands();
        guild.commands.add(new DiscordSlashCommandRegistrar.CommandSnapshot(
                "foreign",
                "link",
                "Command owned by another integration",
                true
        ));
        DiscordSlashCommandRegistrar registrar = new DiscordSlashCommandRegistrar("link");

        assertThatThrownBy(() -> registrar.ensureRegistered(guild).join())
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("Discord guild command collision: /link is not owned by TensaPlugin");
        assertThat(guild.upserts).hasValue(0);
    }

    private static final class FakeGuildCommands implements DiscordSlashCommandRegistrar.GuildCommands {
        private final List<DiscordSlashCommandRegistrar.CommandSnapshot> commands = new ArrayList<>();
        private final AtomicInteger upserts = new AtomicInteger();
        private CompletableFuture<List<DiscordSlashCommandRegistrar.CommandSnapshot>> listGate;

        @Override
        public CompletableFuture<List<DiscordSlashCommandRegistrar.CommandSnapshot>> list() {
            if (listGate != null) {
                CompletableFuture<List<DiscordSlashCommandRegistrar.CommandSnapshot>> gate = listGate;
                listGate = null;
                return gate;
            }
            return CompletableFuture.completedFuture(List.copyOf(commands));
        }

        @Override
        public CompletableFuture<DiscordSlashCommandRegistrar.CommandSnapshot> upsert(
                DiscordSlashCommandRegistrar.CommandSpec spec
        ) {
            upserts.incrementAndGet();
            commands.removeIf(command -> command.name().equals(spec.name()));
            DiscordSlashCommandRegistrar.CommandSnapshot created =
                    new DiscordSlashCommandRegistrar.CommandSnapshot("tensa-link", spec.name(), spec.description(), true);
            commands.add(created);
            return CompletableFuture.completedFuture(created);
        }

        @Override
        public CompletableFuture<Void> delete(String commandId) {
            commands.removeIf(command -> command.id().equals(commandId));
            return CompletableFuture.completedFuture(null);
        }
    }
}
