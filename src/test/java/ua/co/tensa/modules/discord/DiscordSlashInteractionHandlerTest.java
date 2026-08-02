package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordSlashInteractionHandlerTest {
    @Test
    void acknowledgesBeforeRunningAccountLinkingAndEditsTheDeferredReply() {
        List<String> order = new ArrayList<>();
        DiscordSlashInteractionHandler handler = new DiscordSlashInteractionHandler(
                "link",
                "guild-one",
                (code, userId, userName) -> {
                    order.add("link");
                    assertThat(code).isEqualTo("A1B2C3D4");
                    return CompletableFuture.completedFuture(DiscordEmbedMessage.success("linked"));
                }
        );
        FakeInteraction interaction = new FakeInteraction(order, "link", "guild-one");

        assertThat(handler.handle(interaction).join()).isTrue();

        assertThat(order).containsExactly("defer-ephemeral", "link", "edit");
        assertThat(interaction.reply).isNotNull();
        assertThat(interaction.reply.description()).isEqualTo("linked");
    }

    @Test
    void acknowledgesWrongGuildUsageWithoutCallingTheLinkService() {
        AtomicInteger linkCalls = new AtomicInteger();
        List<String> order = new ArrayList<>();
        DiscordSlashInteractionHandler handler = new DiscordSlashInteractionHandler(
                "link",
                "guild-one",
                (code, userId, userName) -> {
                    linkCalls.incrementAndGet();
                    return CompletableFuture.completedFuture(DiscordEmbedMessage.success("linked"));
                }
        );
        FakeInteraction interaction = new FakeInteraction(order, "link", "another-guild");

        assertThat(handler.handle(interaction).join()).isTrue();

        assertThat(order).containsExactly("defer-ephemeral", "edit");
        assertThat(linkCalls).hasValue(0);
        assertThat(interaction.reply.description()).contains("налаштованому Discord-сервері");
    }

    @Test
    void closedHandlerIgnoresLateEventsFromTheOldJdaListener() {
        List<String> order = new ArrayList<>();
        DiscordSlashInteractionHandler handler = new DiscordSlashInteractionHandler(
                "link",
                "guild-one",
                (code, userId, userName) -> CompletableFuture.completedFuture(DiscordEmbedMessage.success("linked"))
        );
        handler.close();

        assertThat(handler.handle(new FakeInteraction(order, "link", "guild-one")).join()).isFalse();
        assertThat(order).isEmpty();
    }

    private static final class FakeInteraction implements DiscordSlashInteractionHandler.Interaction {
        private final List<String> order;
        private final String commandName;
        private final String guildId;
        private DiscordEmbedMessage reply;

        private FakeInteraction(List<String> order, String commandName, String guildId) {
            this.order = order;
            this.commandName = commandName;
            this.guildId = guildId;
        }

        @Override
        public String commandName() {
            return commandName;
        }

        @Override
        public String guildId() {
            return guildId;
        }

        @Override
        public String code() {
            return "A1B2C3D4";
        }

        @Override
        public String userId() {
            return "discord-user";
        }

        @Override
        public String userName() {
            return "Pilot";
        }

        @Override
        public CompletableFuture<DiscordSlashInteractionHandler.DeferredReply> deferEphemeral() {
            order.add("defer-ephemeral");
            return CompletableFuture.completedFuture(message -> {
                order.add("edit");
                reply = message;
                return CompletableFuture.completedFuture(null);
            });
        }
    }
}
