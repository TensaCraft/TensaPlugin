package ua.co.tensa.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import ua.co.tensa.config.ConfigRegistry;
import ua.co.tensa.config.Lang;
import ua.co.tensa.modules.Modules;
import ua.co.tensa.modules.chat.ChatCommands;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ReloadCommand implements SimpleCommand {

	@Override
	public void execute(final Invocation invocation) {
		CommandSource source = invocation.source();
		if (!hasPermission(invocation)) {
			Message.sendLang(source, Lang.no_perms);
			return;
		}
		if (Tensa.database != null) {
			Tensa.database.close();
		}
		// core reload pipeline
		Tensa.loadPlugin();
		// Ensure any config instances registered are reloaded uniformly
		ConfigRegistry.reloadAll();
        // Re-register chat commands based on refreshed config
        ChatCommands.reload();
        // Apply module enable/disable changes and reload enabled modules
        Modules.applyConfig();
        Modules.reloadAll();
		Message.sendLang(source, Lang.reload);
	}

	@Override
	public boolean hasPermission(final Invocation invocation) {
		return invocation.source().hasPermission("tensa.reload");
	}

	@Override
	public CompletableFuture<List<String>> suggestAsync(final Invocation invocation) {
		return CompletableFuture.completedFuture(List.of());
	}
}
