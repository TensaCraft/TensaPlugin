package ua.co.tensa.modules.rcon.manager;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import ua.co.tensa.Util;
import ua.co.tensa.config.Lang;
import ua.co.tensa.modules.rcon.data.RconManagerConfig;

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
            try {
                RconManagerConfig.get().reloadCfg();
                // Trigger module reload to reinitialize everything properly
                ua.co.tensa.modules.rcon.manager.RconManagerModule.ENTRY.reload();
                Message.sendLang(sender, Lang.rcon_manager_reload);
            } catch (Exception e) {
                Message.sendLang(sender, Lang.unknown_error);
                Message.rcon("RELOAD FAILED", e.getMessage());
            }
            return;
        }

		String command = buildCommand(args, server);

		if (command.isEmpty()) {
			Message.sendLang(sender, Lang.rcon_empty_command);
			return;
		}

        if ("all".equalsIgnoreCase(server)) {
            executeCommandForAllServers(invocation, command, sender);
        } else if (RconManagerModule.serverIs(server)) {
            executeCommandForServer(invocation, command, sender, server);
        } else {
            Message.rcon("SERVER NOT FOUND", "'" + server + "' not in config");
        }
	}

	@Override
	public boolean hasPermission(final Invocation invocation) {
        return invocation.source().hasPermission("tensa.rcon");
	}

	public boolean hasPermission(final Invocation invocation, String server) {
        return invocation.source().hasPermission("tensa.rcon." + server);
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
        String[] commands = { "rcon", "trcon" };
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
			String ip = RconManagerModule.getIP(server);
			Integer port = RconManagerModule.getPort(server);
			String password = RconManagerModule.getPass(server);

        // Minimal trace only on error; avoid noisy logs on success

			Rcon rcon = new Rcon(ip, port, password.getBytes());
			String result = rcon.command(command.trim());
			rcon.disconnect(); // Close connection after command

			if (result.isEmpty()) {
				result = Lang.rcon_response_empty.getClean();
			} else {
				// Strip MiniMessage and legacy color codes from server response
				result = stripFormattingCodes(result);
			}

			// Format multi-line responses with proper indentation
			result = formatMultilineResponse(result);

			// Always inform the invoker, including console
			Message.sendLang(sender, Lang.rcon_response, "{server}", Util.capitalize(server), "{response}", result);
        } catch (UnknownHostException e) {
            Message.sendLang(sender, Lang.rcon_unknown_error, "{server}", Util.capitalize(server));
        } catch (IOException e) {
            Message.sendLang(sender, Lang.rcon_io_error, "{server}", Util.capitalize(server));
        } catch (AuthenticationException e) {
            Message.sendLang(sender, Lang.rcon_auth_error, "{server}", Util.capitalize(server));
        } catch (Exception e) {
            // Catch any other exceptions
            Message.sendLang(sender, Lang.unknown_error);
            // For debugging - log detailed errors:
            Message.rcon("COMMAND ERROR", server + " → " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
	}

	private String buildCommand(String[] args, String server) {
		if (args.length <= 1) return "";
		return String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
	}

	// Cached patterns for better performance
	private static final java.util.regex.Pattern HEX_COLOR_PATTERN =
		java.util.regex.Pattern.compile("[§&]x(?:[§&][0-9a-fA-F]){6}");
	private static final java.util.regex.Pattern LEGACY_COLOR_PATTERN =
		java.util.regex.Pattern.compile("[§&][0-9a-fk-orA-FK-OR]");
	private static final java.util.regex.Pattern MINIMESSAGE_TAG_PATTERN =
		java.util.regex.Pattern.compile("<[^>]*>");
	private static final java.util.regex.Pattern EMPTY_LINES_PATTERN =
		java.util.regex.Pattern.compile("(?m)^\\s*$\\n");
	private static final java.util.regex.Pattern MULTIPLE_SPACES_PATTERN =
		java.util.regex.Pattern.compile(" {2,}");

	private String stripFormattingCodes(String input) {
		if (input == null || input.isEmpty()) return "";

		// Use cached patterns for better performance
		String result = HEX_COLOR_PATTERN.matcher(input).replaceAll("");
		result = LEGACY_COLOR_PATTERN.matcher(result).replaceAll("");
		result = MINIMESSAGE_TAG_PATTERN.matcher(result).replaceAll("");
		result = EMPTY_LINES_PATTERN.matcher(result).replaceAll("");
		result = MULTIPLE_SPACES_PATTERN.matcher(result).replaceAll(" ");

		return result.trim();
	}

	private String formatMultilineResponse(String input) {
		if (input == null || input.isEmpty()) return "";

		String[] lines = input.split("\n");
		if (lines.length == 1) return input;

		StringBuilder formatted = new StringBuilder();
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i].trim();
			if (!line.isEmpty()) {
				if (i > 0) formatted.append("\n  "); // Indent continuation lines
				formatted.append(line);
			}
		}

		return formatted.toString();
	}

}
