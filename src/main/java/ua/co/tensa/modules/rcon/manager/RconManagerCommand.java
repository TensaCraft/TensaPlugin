package ua.co.tensa.modules.rcon.manager;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import ua.co.tensa.Util;
import ua.co.tensa.config.Lang;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class RconManagerCommand implements SimpleCommand {

	@Override
	public void execute(final Invocation invocation) {
		CommandSource sender = invocation.source();
		String[] args = invocation.arguments();

		if (!hasPermission(invocation)) {
			Message.sendLang(sender, Lang.no_perms);
			return;
		}

		if (args.length < 1) {
			Message.sendLang(sender, Lang.rcon_usage);
			return;
		}

		String server = args[0];

        if (args.length == 1 && "reload".equals(server) && hasPermission(invocation, "reload")) {
            ua.co.tensa.config.data.RconManagerYAML.getInstance().getReloadedFile();
            Message.sendLang(sender, Lang.rcon_manager_reload);
            return;
        }

		String command = buildCommand(args, server);

		if (command.isEmpty()) {
			Message.sendLang(sender, Lang.rcon_empty_command);
			return;
		}

		if ("all".equals(server)) {
			executeCommandForAllServers(invocation, command, sender);
		} else if (RconManagerModule.serverIs(server)) {
			executeCommandForServer(invocation, command, sender, server);
		}
	}

	@Override
	public boolean hasPermission(final Invocation invocation) {
		return invocation.source().hasPermission("TENSA.rcon");
	}

	public boolean hasPermission(final Invocation invocation, String server) {
		return invocation.source().hasPermission("TENSA.rcon." + server);
	}

	@Override
	public CompletableFuture<List<String>> suggestAsync(final Invocation invocation) {
		ArrayList<String> args = new ArrayList<>();
		int argNum = invocation.arguments().length;
		if (argNum == 0) {
			args = (ArrayList<String>) RconManagerModule.getServers();
			args.add("all");
			args.add("reload");
		}
		if (argNum == 2) {
			args.addAll(RconManagerModule.getCommandArgs());
		}
		if (argNum > 2) {
			for (Player player : Tensa.server.getAllPlayers()) {
				args.add(player.getUsername().trim());
			}
		}
		return CompletableFuture.completedFuture(args);
	}

	public static void unregister() {
		CommandManager manager = Tensa.server.getCommandManager();
        String[] commands = { "vurcon", "rcon", "velocityrcon" };
		for (String command : commands) {
			manager.unregister(command);
		}
	}

	private void executeCommandForServer(Invocation invocation, String command, CommandSource sender,
										 String server) {
		if (hasPermission(invocation, "all") || hasPermission(invocation, server)) {
			tryExecuteRconCommand(command, sender, server);
		} else {
			Message.sendLang(sender, Lang.no_perms);
		}
	}

	private void executeCommandForAllServers(Invocation invocation, String command, CommandSource sender) {
		for (String server_name : RconManagerModule.getServers()) {
			if (hasPermission(invocation, "all") || hasPermission(invocation, server_name)) {
				tryExecuteRconCommand(command, sender, server_name);
			} else {
				Message.sendLang(sender, Lang.no_perms);
			}
		}
	}

	private void tryExecuteRconCommand(String command, CommandSource sender, String server) {
		try {
			Rcon rcon = new Rcon(RconManagerModule.getIP(server), RconManagerModule.getPort(server),
					RconManagerModule.getPass(server).getBytes());
			String result = rcon.command(command.trim());
			if (result.isEmpty()) {
				result = Lang.rcon_response_empty.getClean();
			}
			Message.sendLang(sender, Lang.rcon_response, "{server}", Util.capitalize(server), "{response}", result);
		} catch (UnknownHostException e) {
			Message.sendLang(sender, Lang.rcon_unknown_error, "{server}", Util.capitalize(server));
		} catch (IOException e) {
			Message.sendLang(sender, Lang.rcon_io_error, "{server}", Util.capitalize(server));
		} catch (AuthenticationException e) {
			Message.sendLang(sender, Lang.rcon_auth_error, "{server}", Util.capitalize(server));
		}
	}

	private String buildCommand(String[] args, String server) {
		StringBuilder sb = new StringBuilder();
		for (int i = 1; i < args.length; i++) {
			sb.append(args[i]).append(" ");
		}
		return sb.toString().trim();
	}

}
