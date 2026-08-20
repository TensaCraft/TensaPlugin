package ua.co.tensa.modules.discord;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

final class DiscordCommand implements SimpleCommand {
    private final DiscordRuntime runtime;

    DiscordCommand(DiscordRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            DiscordMessages.send(
                    invocation.source(),
                    "discord_player_only",
                    "<red>Ця команда доступна лише гравцям.</red>",
                    Map.of()
            );
            return;
        }

        String action = invocation.arguments().length == 0
                ? ""
                : invocation.arguments()[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "link" -> link(player);
            case "unlink" -> unlink(player);
            case "status" -> status(player);
            default -> usage(player);
        }
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        if (invocation.arguments().length > 1) {
            return CompletableFuture.completedFuture(List.of());
        }
        String prefix = invocation.arguments().length == 0
                ? ""
                : invocation.arguments()[0].toLowerCase(Locale.ROOT);
        return CompletableFuture.completedFuture(List.of("link", "unlink", "status").stream()
                .filter(value -> value.startsWith(prefix))
                .toList());
    }

    private void link(Player player) {
        DiscordLinkService.IssuedCode issued = runtime.issueLinkCode(
                player.getUniqueId(),
                player.getUsername()
        );
        if (!issued.issued()) {
            DiscordMessages.send(
                    player,
                    "discord_link_already",
                    "<yellow>Ваш Minecraft-акаунт уже прив'язаний до Discord.</yellow>",
                    Map.of()
            );
            return;
        }
        long minutes = Math.max(1L, (Duration.between(java.time.Instant.now(), issued.expiresAt()).toSeconds() + 59L) / 60L);
        DiscordMessages.send(
                player,
                "discord_link_code",
                "<green>Код прив'язки:</green> <white>{code}</white> "
                        + "<click:copy_to_clipboard:'{code}'><hover:show_text:'<gray>Натисніть, щоб скопіювати</gray>'>"
                        + "<aqua>[Скопіювати]</aqua></hover></click>"
                        + "<gray>. У Discord виконайте </gray><white>/{command} code:{code}</white>"
                        + "<gray>. Код діє {minutes} хв.</gray>",
                Map.of(
                        "code", issued.code(),
                        "command", runtime.linkCommandName(),
                        "minutes", Long.toString(minutes)
                )
        );
    }

    private void status(Player player) {
        runtime.linkStatus(player.getUniqueId()).whenComplete((account, error) -> {
            if (error != null) {
                sendBusy(player);
                return;
            }
            if (account.isEmpty()) {
                DiscordMessages.send(
                        player,
                        "discord_status_unlinked",
                        "<gray>Discord-акаунт не прив'язано.</gray>",
                        Map.of()
                );
                return;
            }
            runtime.reconcileLinkedRole(player.getUniqueId());
            DiscordMessages.send(
                    player,
                    "discord_status_linked",
                    "<green>Прив'язано до Discord:</green> <white>{discord}</white>",
                    Map.of("discord", DiscordSanitizer.forMinecraft(account.get().discordUserName(), 80))
            );
        });
    }

    private void unlink(Player player) {
        runtime.unlink(player.getUniqueId()).whenComplete((result, error) -> {
            if (error != null) {
                sendBusy(player);
                return;
            }
            switch (result.type()) {
                case UNLINKED -> DiscordMessages.send(
                        player,
                        "discord_unlink_success",
                        "<green>Discord-акаунт відв'язано.</green>",
                        Map.of()
                );
                case NOT_LINKED -> DiscordMessages.send(
                        player,
                        "discord_status_unlinked",
                        "<gray>Discord-акаунт не прив'язано.</gray>",
                        Map.of()
                );
                case ROLE_FAILED -> DiscordMessages.send(
                        player,
                        "discord_unlink_role_failed",
                        "<red>Не вдалося зняти Discord-роль. Прив'язку залишено без змін.</red>",
                        Map.of()
                );
                default -> DiscordMessages.send(
                        player,
                        "discord_link_error",
                        "<red>Не вдалося змінити прив'язку. Спробуйте пізніше.</red>",
                        Map.of()
                );
            }
        });
    }

    private void usage(Player player) {
        DiscordMessages.send(
                player,
                "discord_usage",
                "<gold>Використання:</gold> <white>/discord link|unlink|status</white>",
                Map.of()
        );
    }

    private void sendBusy(Player player) {
        DiscordMessages.send(
                player,
                "discord_link_busy",
                "<yellow>Сервіс прив'язки зараз зайнятий. Спробуйте пізніше.</yellow>",
                Map.of()
        );
    }
}
