package ua.co.tensa.modules.rcon.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import ua.co.tensa.config.Lang;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class RconHandler extends SimpleChannelInboundHandler<ByteBuf> {

	private static final byte FAILURE = -1;
	private static final byte TYPE_RESPONSE = 0;
	private static final byte TYPE_COMMAND = 2;
	private static final byte TYPE_LOGIN = 3;

	// Rate limiting: track failed login attempts per IP
	private static final int MAX_FAILED_ATTEMPTS = 3;
	private static final long RATE_LIMIT_WINDOW_MS = 5000; // 5 seconds
	private static final long BLOCK_DURATION_MS = 300000; // 5 minutes
	private static final RconLoginRateLimiter LOGIN_LIMITER = new RconLoginRateLimiter(
			RATE_LIMIT_WINDOW_MS, BLOCK_DURATION_MS, MAX_FAILED_ATTEMPTS, 4_096
	);

	private final String password;

	private boolean loggedIn = false;

	private final RconServer rconServer;

	public RconHandler(RconServer rconServer, String password) {
		this.rconServer = rconServer;
		this.password = password;
	}

	@Override
	@SuppressWarnings("deprecation")
	protected void channelRead0(ChannelHandlerContext ctx, ByteBuf buf) {
		buf = buf.order(ByteOrder.LITTLE_ENDIAN);
		if (buf.readableBytes() < 10) {
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
        String remoteAddress = remoteAddressKey(ctx.channel().remoteAddress());
        long currentTime = System.currentTimeMillis();
        RconLoginRateLimiter.Decision decision = LOGIN_LIMITER.check(remoteAddress, currentTime);
        if (decision == RconLoginRateLimiter.Decision.BLOCKED) {
            ua.co.tensa.Message.rcon("LOGIN BLOCKED", remoteAddress + " → Too many failed attempts");
            ctx.close();
            return;
        }
        if (decision == RconLoginRateLimiter.Decision.RATE_LIMITED) {
            ua.co.tensa.Message.rcon("RATE LIMITED", remoteAddress + " → Too many requests");
            ctx.close();
            return;
        }

        if (password.equals(payload)) {
            loggedIn = true;
            // Clear failed attempts on success
            LOGIN_LIMITER.success(remoteAddress);
            // Many RCON clients expect two packets on successful auth:
            // an empty RESPONSE_VALUE followed by AUTH_RESPONSE
            sendResponse(ctx, requestId, TYPE_RESPONSE, "");
            sendResponse(ctx, requestId, TYPE_COMMAND, "");
        } else {
            loggedIn = false;
            // Increment failed attempts
            int failures = LOGIN_LIMITER.failure(remoteAddress, currentTime);
            ua.co.tensa.Message.rcon("AUTH FAILED", remoteAddress + " → Invalid password (attempt " +
                failures + "/" + MAX_FAILED_ATTEMPTS + ")");

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
		String ip = ctx.channel().remoteAddress().toString().replace("/", "");
        String commandLabel = commandLabel(payload);
        // Optional debug logging (configurable to prevent spam)
        if (RconServerModule.isDebugEnabled()) {
            ua.co.tensa.Message.info(ua.co.tensa.text.TextPipeline.interpolate(
                    Lang.rcon_connect_notify.getClean(),
                    java.util.Map.of("address", ip, "command", commandLabel)
            ));
        }

		// Only notify players if debug is enabled (to prevent spam)
		if (RconServerModule.isDebugEnabled()) {
			Tensa.server.getAllPlayers().forEach(p -> {
				if (p.getPermissionValue("tensa.rcon.notify").asBoolean()) {
	                Message.sendLang(p, ua.co.tensa.config.Lang.rcon_connect_notify,
                        "{address}", ip, "{command}", commandLabel);
				}
			});
		}

		if (payload.equalsIgnoreCase("end") || payload.equalsIgnoreCase("stop")) {
			String message = "Shutting down the proxy...";
            if (!RconServerModule.isColored()) {
                message = RconServerModule.stripColor(message);
            }
            sendCommandResponse(ctx, requestId, message);
            Tensa.server.shutdown();
            return;
		} else {
            RconCommandSource commandSender = new RconCommandSource(rconServer.getServer());
            rconServer.getServer().getCommandManager().executeAsync(commandSender, payload)
                    .whenComplete((executed, throwable) -> {
                        boolean commandSuccess = throwable == null && Boolean.TRUE.equals(executed);
                        String responseMessage;
                        if (throwable != null) {
                            if (RconServerModule.isErrorLoggingEnabled()) {
                                ua.co.tensa.Message.rcon("EXECUTION ERROR", throwable.getClass().getSimpleName());
                            }
                            responseMessage = Lang.unknown_error.getClean();
                        } else if (commandSuccess) {
                            responseMessage = commandSender.flush();
                        } else {
                            responseMessage = Lang.no_command.getClean();
                        }

                        if (!commandSuccess) {
                            if (RconServerModule.isErrorLoggingEnabled()) {
                                String errorMsg = String.format(Lang.error_executing.getClean() + " %s (%s)", commandLabel, responseMessage);
                                ua.co.tensa.Message.info(String.format("RCON Error from %s: %s", ip, errorMsg));
                                responseMessage = errorMsg;
                            } else {
                                responseMessage = "Command failed";
                            }
                        }

                        if (!RconServerModule.isColored()) {
                            responseMessage = RconServerModule.stripColor(responseMessage);
                        }

                        sendCommandResponse(ctx, requestId, responseMessage);
                    });
            return;
		}
	}

    private void sendCommandResponse(ChannelHandlerContext ctx, int requestId, String message) {
        if (!ctx.channel().isActive()) {
            return;
        }
        ctx.executor().execute(() -> {
            if (!ctx.channel().isActive()) {
                return;
            }
            sendLargeResponse(ctx, requestId, message);
            sendResponse(ctx, requestId, TYPE_RESPONSE, "");
        });
    }

    private static String commandLabel(String payload) {
        if (payload == null || payload.isBlank()) {
            return "[empty]";
        }
        String trimmed = payload.trim();
        int separator = trimmed.indexOf(' ');
        String name = separator < 0 ? trimmed : trimmed.substring(0, separator);
        return name.length() > 64 ? name.substring(0, 64) : name;
    }

    static String remoteAddressKey(SocketAddress address) {
        if (address instanceof InetSocketAddress inet) {
            return inet.getAddress() == null ? inet.getHostString() : inet.getAddress().getHostAddress();
        }
        return address == null ? "unknown" : address.toString();
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
    public void userEventTriggered(ChannelHandlerContext ctx, Object event) throws Exception {
        if (event instanceof IdleStateEvent idle && idle.state() == IdleState.READER_IDLE) {
            ctx.close();
        } else {
            super.userEventTriggered(ctx, event);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof java.net.SocketException && "Connection reset".equalsIgnoreCase(cause.getMessage())) {
            // Common when remote closes abruptly; suppress noisy stacktrace
        } else {
            ua.co.tensa.Message.rcon("PIPELINE ERROR", cause.getClass().getSimpleName());
        }
        try { ctx.close(); } catch (Throwable ignored) {}
    }
}
