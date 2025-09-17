package ua.co.tensa.modules.rcon.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import ua.co.tensa.config.Lang;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class RconHandler extends SimpleChannelInboundHandler<ByteBuf> {

	private static final byte FAILURE = -1;
	private static final byte TYPE_RESPONSE = 0;
	private static final byte TYPE_COMMAND = 2;
	private static final byte TYPE_LOGIN = 3;

	private final String password;

	private boolean loggedIn = false;

	private final RconServer rconServer;

	private final RconCommandSource commandSender;

	public RconHandler(RconServer rconServer, String password) {
		this.rconServer = rconServer;
		this.password = password;
		this.commandSender = new RconCommandSource(rconServer.getServer());
	}

	@Override
	@SuppressWarnings("deprecation")
	protected void channelRead0(ChannelHandlerContext ctx, ByteBuf buf) {
		buf = buf.order(ByteOrder.LITTLE_ENDIAN);
		if (buf.readableBytes() < 8) {
			return;
		}

		int requestId = buf.readInt();
		int type = buf.readInt();

		byte[] payloadData = new byte[buf.readableBytes() - 2];
		buf.readBytes(payloadData);
		String payload = new String(payloadData, StandardCharsets.UTF_8);

		buf.readBytes(2); // two byte padding

		if (type == TYPE_LOGIN) {
			handleLogin(ctx, payload, requestId);
		} else if (type == TYPE_COMMAND) {
			handleCommand(ctx, payload, requestId);
		} else {
			sendLargeResponse(ctx, requestId, Lang.unknown_request.getClean() + " " + Integer.toHexString(type));
		}
	}

    private void handleLogin(ChannelHandlerContext ctx, String payload, int requestId) {
        if (password.equals(payload)) {
            loggedIn = true;
            // Many RCON clients expect two packets on successful auth:
            // an empty RESPONSE_VALUE followed by AUTH_RESPONSE
            sendResponse(ctx, requestId, TYPE_RESPONSE, "");
            sendResponse(ctx, requestId, TYPE_COMMAND, "");
        } else {
            loggedIn = false;
            // Send both empty RESPONSE_VALUE and AUTH_RESPONSE with failure id (-1)
            sendResponse(ctx, FAILURE, TYPE_RESPONSE, "");
            sendResponse(ctx, FAILURE, TYPE_COMMAND, "");
        }
    }

	private void handleCommand(ChannelHandlerContext ctx, String payload, int requestId) {
		if (!loggedIn) {
			sendResponse(ctx, FAILURE, TYPE_COMMAND, "");
			return;
		}
		boolean stop = false;
		boolean success;
		String message;
		String ip = ctx.channel().remoteAddress().toString().replace("/", "");
        // Optional debug logging (configurable to prevent spam)
        if (RconServerModule.isDebugEnabled()) {
            ua.co.tensa.Message.info(Lang.rcon_connect_notify.getClean().replace("{address}", ip).replace("{command}", payload));
        }

		// Only notify players if debug is enabled (to prevent spam)
		if (RconServerModule.isDebugEnabled()) {
			Tensa.server.getAllPlayers().forEach(p -> {
				if (p.getPermissionValue("TENSA.rcon.notify").asBoolean()) {
	                Message.sendLang(p, ua.co.tensa.config.Lang.rcon_connect_notify,
	                        "{address}", ip, "{command}", payload);
				}
			});
		}

		if (payload.equalsIgnoreCase("end") || payload.equalsIgnoreCase("stop")) {
			stop = true;
			success = true;
			message = "Shutting down the proxy...";
		} else {
			try {
				success = rconServer.getServer().getCommandManager().executeAsync(commandSender, payload).join();
				if (success) {
					message = commandSender.flush();
				} else {
					message = Lang.no_command.getClean();
				}
            } catch (Exception e) {
                // Only log exceptions if error logging is enabled
                if (RconServerModule.isErrorLoggingEnabled()) {
                    ua.co.tensa.Message.error(e.getMessage());
                }
				success = false;
				message = Lang.unknown_error.getClean();
			}
		}

		if (!success) {
			// Only log and format detailed errors if error logging is enabled
			if (RconServerModule.isErrorLoggingEnabled()) {
				String errorMsg = String.format(Lang.error_executing.getClean() + " %s (%s)", payload, message);
				ua.co.tensa.Message.info(String.format("RCON Error from %s: %s", ip, errorMsg));
				message = errorMsg;
			} else {
				// Just return a generic error without logging details to console or sending to RCON client
				message = "Command failed";
			}
		}

        if (!RconServerModule.isColored()) {
            message = RconServerModule.stripColor(message);
        }

        // Some clients expect a leading empty RESPONSE_VALUE before the actual content
        sendResponse(ctx, requestId, TYPE_RESPONSE, "");
        sendLargeResponse(ctx, requestId, message);

		if (stop) {
			Tensa.server.shutdown();
		}
	}

    private void sendResponse(ChannelHandlerContext ctx, int requestId, int type, String payload) {
		@SuppressWarnings("deprecation")
		ByteBuf buf = ctx.alloc().buffer().order(ByteOrder.LITTLE_ENDIAN);
		buf.writeInt(requestId);
		buf.writeInt(type);
		buf.writeBytes(payload.getBytes(StandardCharsets.UTF_8));
		buf.writeByte(0);
		buf.writeByte(0);
		ctx.writeAndFlush(buf);
	}

    private void sendLargeResponse(ChannelHandlerContext ctx, int requestId, String payload) {
        if (payload.isEmpty()) {
            sendResponse(ctx, requestId, TYPE_RESPONSE, "");
            return;
        }

        int start = 0;
        while (start < payload.length()) {
            int length = payload.length() - start;
            int truncated = Math.min(length, 2048);

            // substring end index is exclusive; add start offset
            int end = start + truncated;
            sendResponse(ctx, requestId, TYPE_RESPONSE, payload.substring(start, end));
            start = end;
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof java.net.SocketException && "Connection reset".equalsIgnoreCase(cause.getMessage())) {
            // Common when remote closes abruptly; suppress noisy stacktrace
        } else {
            ua.co.tensa.Message.warn("RCON pipeline error: " + cause.getMessage());
        }
        try { ctx.close(); } catch (Throwable ignored) {}
    }
}
