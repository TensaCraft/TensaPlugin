package ua.co.tensa.modules.rcon.server;

import com.velocitypowered.api.proxy.ProxyServer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import ua.co.tensa.Tensa;
import ua.co.tensa.modules.AbstractModule;
import ua.co.tensa.modules.ModuleEntry;
import ua.co.tensa.modules.rcon.data.RconServerConfig;

import java.net.InetSocketAddress;
import java.util.regex.Pattern;

public class RconServerModule {

    private static final ModuleEntry IMPL = new AbstractModule(
            "rcon-server", "Rcon Server") {
        @Override protected void onEnable() {
            ua.co.tensa.modules.rcon.data.RconServerConfig.get().reloadCfg();
            // status logging handled centrally
            startRconListener();
        }
        @Override protected void onDisable() {
            stopRconListener();
        }
        @Override protected void onReload() {
            ua.co.tensa.modules.rcon.data.RconServerConfig.get().reloadCfg();
            stopRconListener();
            startRconListener();
            // status logging handled centrally
        }
    };
    public static final ModuleEntry ENTRY = IMPL;

	private static RconServer rconServer;
	private static final ProxyServer server = Tensa.server;
	public static final char COLOR_CHAR = '\u00A7';
	public static final Pattern STRIP_COLOR_PATTERN = Pattern.compile("(?i)" + COLOR_CHAR + "[0-9A-FK-OR]");
	public static final Pattern STRIP_MC_COLOR_PATTERN = Pattern.compile("§[0-8abcdefklmnor]");
    public static Integer getPort() {
        return RconServerConfig.get().port;
    }

    public static String getPass() {
        return RconServerConfig.get().password;
    }

    public static boolean isColored() {
        return RconServerConfig.get().colored;
    }

    public static boolean isDebugEnabled() {
        return RconServerConfig.get().debug;
    }

    public static boolean isErrorLoggingEnabled() {
        return RconServerConfig.get().logErrors;
    }

	public static String stripColor(final String input) {
		if (input == null) {
			return null;
		}

		return STRIP_COLOR_PATTERN.matcher(input).replaceAll("");
	}

	public static String stripMcColor(final String input) {
		if (input == null) {
			return null;
		}

		return STRIP_MC_COLOR_PATTERN.matcher(input).replaceAll("");
	}

	public static boolean isInteger(String str) {
		return str.matches("-?\\d+");
	}

    private static void startRconListener() {
        InetSocketAddress address = new InetSocketAddress(getPort());
        rconServer = new RconServer(server, getPass());
        try {
            ChannelFuture future = rconServer.bind(address).syncUninterruptibly();
            Channel channel = future.channel();
            if (channel != null && channel.isActive()) {
                ua.co.tensa.Message.info("Binding rcon to address: " + address.getHostName() + ":" + address.getPort());
            } else {
                ua.co.tensa.Message.warn("Failed to bind RCON to " + address.getHostName() + ":" + address.getPort());
                stopRconListener();
            }
        } catch (Throwable t) {
            ua.co.tensa.Message.error("RCON bind error: " + t.getMessage());
            stopRconListener();
        }
    }

	private static void stopRconListener() {
		if (rconServer != null) {
			ua.co.tensa.Message.info("Trying to stop RCON listener");
			rconServer.shutdown();
		}
	}

    public static void enable() { IMPL.enable(); }
    public static void disable() { IMPL.disable(); }

}
