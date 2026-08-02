package ua.co.tensa.modules.rcon.manager;

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
            RconManagerModule.supplyAsync(() -> {
                if (RconManagerModule.ENTRY.tryReload()) {
                    Message.sendLang(sender, Lang.rcon_manager_reload);
                } else {
                    Message.sendLang(sender, Lang.unknown_error);
                }
                return null;
            }).exceptionally(error -> {
                Message.sendLang(sender, Lang.unknown_error);
                Message.rcon("RELOAD FAILED", error.getClass().getSimpleName());
                return null;
            });
            return;
        }

		String command = buildCommand(args);

		if (command.isEmpty()) {
			Message.sendLang(sender, Lang.rcon_empty_command);
			return;
		}

        RconManagerSettings.RconConnection connection = RconManagerModule.connection(server);
        if ("all".equalsIgnoreCase(server)) {
            executeCommandForAllServers(invocation, command, sender);
        } else if (connection != null) {
            executeCommandForServer(invocation, command, sender, server, connection);
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
            args.addAll(RconManagerModule.getServers());
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
        String[] commands = { "rcon", "trcon" };
		for (String command : commands) {
			Util.unregisterCommand(command);
		}
	}

	private void executeCommandForServer(Invocation invocation, String command, CommandSource sender,
										 String server, RconManagerSettings.RconConnection connection) {
		if (hasPermission(invocation, "all") || hasPermission(invocation, server)) {
            RconManagerModule.supplyAsync(() -> {
                tryExecuteRconCommand(command, sender, server, connection);
                return null;
            }).exceptionally(error -> {
                Message.privateMessage(sender, "<yellow>RCON command queue is busy. Try again later.</yellow>");
                return null;
            });
		} else {
			Message.sendLang(sender, Lang.no_perms);
		}
	}

	private void executeCommandForAllServers(Invocation invocation, String command, CommandSource sender) {
        List<String> allowedServers = RconManagerModule.getServers().stream()
                .filter(serverName -> hasPermission(invocation, "all") || hasPermission(invocation, serverName))
                .toList();

        if (allowedServers.isEmpty()) {
            Message.sendLang(sender, Lang.no_perms);
            return;
        }

		java.util.Map<String, RconManagerSettings.RconConnection> targets = new java.util.LinkedHashMap<>();
        for (String serverName : allowedServers) {
            RconManagerSettings.RconConnection connection = RconManagerModule.connection(serverName);
            if (connection != null) {
                targets.put(serverName, connection);
            }
        }
        RconManagerModule.supplyAsync(() -> {
            for (java.util.Map.Entry<String, RconManagerSettings.RconConnection> target : targets.entrySet()) {
                tryExecuteRconCommand(command, sender, target.getKey(), target.getValue());
            }
            return null;
        }).exceptionally(error -> {
            Message.privateMessage(sender, "<yellow>RCON command queue is busy. Try again later.</yellow>");
            return null;
        });
	}

	private void tryExecuteRconCommand(
            String command,
            CommandSource sender,
            String server,
            RconManagerSettings.RconConnection connection
    ) {
		try {
        // Minimal trace only on error; avoid noisy logs on success

			byte[] password = connection.password().getBytes(java.nio.charset.StandardCharsets.UTF_8);
			String result;
			try (Rcon rcon = new Rcon(connection.host(), connection.port(), password)) {
				result = rcon.command(command.trim());
			} finally {
				java.util.Arrays.fill(password, (byte) 0);
			}

			if (result.isEmpty()) {
				result = Lang.rcon_response_empty.getClean();
			} else {
				// Strip MiniMessage and legacy color codes from server response
				result = stripFormattingCodes(result);
			}

			// Format multi-line responses with proper indentation
			result = formatMultilineResponse(result);

			// Always inform the invoker, including console
			Message.sendLang(sender, Lang.rcon_response,
                    "{server}", Message.escapeMiniMessage(Util.capitalize(server)),
                    "{response}", Message.escapeMiniMessage(result));
        } catch (UnknownHostException e) {
            Message.sendLang(sender, Lang.rcon_unknown_error, "{server}", Message.escapeMiniMessage(Util.capitalize(server)));
        } catch (IOException e) {
            Message.sendLang(sender, Lang.rcon_io_error, "{server}", Message.escapeMiniMessage(Util.capitalize(server)));
        } catch (AuthenticationException e) {
            Message.sendLang(sender, Lang.rcon_auth_error, "{server}", Message.escapeMiniMessage(Util.capitalize(server)));
        } catch (Exception e) {
            // Catch any other exceptions
            Message.sendLang(sender, Lang.unknown_error);
            // For debugging - log detailed errors:
            Message.rcon("COMMAND ERROR", server + " → " + e.getClass().getSimpleName());
        }
	}

	private String buildCommand(String[] args) {
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
