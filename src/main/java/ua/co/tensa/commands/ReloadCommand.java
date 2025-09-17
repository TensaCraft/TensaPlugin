package ua.co.tensa.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import ua.co.tensa.config.Config;
import ua.co.tensa.config.Lang;
import ua.co.tensa.modules.Modules;

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
        // Close DB if open
        if (Tensa.database != null) {
            Tensa.database.close();
            Tensa.database = null;
        }
        // Reload root config manager (keeps YAML structure)
        if (Tensa.config == null) {
            Tensa.config = new Config();
        } else {
            Tensa.config.reload();
        }
        // Module configs/models are reloaded by each module's onReload();
        // keep only global language reload if needed
        try { ua.co.tensa.config.data.LangYAML.getInstance().reload(); } catch (Throwable ignored) {}
        // Re-init database from config if enabled
        if (Tensa.config.databaseEnable()) {
            Tensa.database = new ua.co.tensa.config.Database();
            if (Tensa.database.connect()) {
                ua.co.tensa.config.DatabaseInitializer initializer = new ua.co.tensa.config.DatabaseInitializer(Tensa.database);
                initializer.initializeTables();
            }
        }
        // Apply module enable/disable changes and soft-reload enabled modules via module API
        Modules.refresh();
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
