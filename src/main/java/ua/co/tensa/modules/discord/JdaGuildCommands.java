package ua.co.tensa.modules.discord;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.util.List;
import java.util.concurrent.CompletableFuture;

final class JdaGuildCommands implements DiscordSlashCommandRegistrar.GuildCommands {
    private final Guild guild;

    JdaGuildCommands(Guild guild) {
        this.guild = guild;
    }

    @Override
    public CompletableFuture<List<DiscordSlashCommandRegistrar.CommandSnapshot>> list() {
        return guild.retrieveCommands().submit().thenApply(commands -> commands.stream()
                .map(JdaGuildCommands::snapshot)
                .toList());
    }

    @Override
    public CompletableFuture<DiscordSlashCommandRegistrar.CommandSnapshot> upsert(
            DiscordSlashCommandRegistrar.CommandSpec spec
    ) {
        return guild.upsertCommand(Commands.slash(spec.name(), spec.description())
                        .addOption(
                                OptionType.STRING,
                                "code",
                                "Одноразовий код із команди /discord link",
                                true
                        ))
                .submit()
                .thenApply(JdaGuildCommands::snapshot);
    }

    @Override
    public CompletableFuture<Void> delete(String commandId) {
        return guild.deleteCommandById(commandId).submit();
    }

    private static DiscordSlashCommandRegistrar.CommandSnapshot snapshot(Command command) {
        boolean requiredCodeOption = command.getOptions().stream().anyMatch(option ->
                option.getName().equals("code")
                        && option.getType() == OptionType.STRING
                        && option.isRequired());
        return new DiscordSlashCommandRegistrar.CommandSnapshot(
                command.getId(),
                command.getName(),
                command.getDescription(),
                requiredCodeOption
        );
    }
}
