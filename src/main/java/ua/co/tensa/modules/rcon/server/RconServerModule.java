package ua.co.tensa.modules.rcon.server;

import com.velocitypowered.api.proxy.ProxyServer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import ua.co.tensa.Tensa;
import ua.co.tensa.modules.rcon.data.RconServerYAML;
import ua.co.tensa.modules.AbstractModule;
import ua.co.tensa.modules.ModuleEntry;

import java.net.InetSocketAddress;
import java.util.regex.Pattern;

public class RconServerModule {

    private static final ModuleEntry IMPL = new AbstractModule(
            "rcon-server", "Rcon Server") {
        @Override protected void onEnable() {
            ua.co.tensa.modules.AbstractModule.ensureConfig(RconServerYAML.getInstance());
            ua.co.tensa.Message.info("Rcon Server module enabled");
            startRconListener();
        }
        @Override protected void onDisable() {
            stopRconListener();
        }
    };
    public static final ModuleEntry ENTRY = IMPL;

	private static RconServer rconServer;
	private static final ProxyServer server = Tensa.server;
	public static final char COLOR_CHAR = '\u00A7';
	public static final Pattern STRIP_COLOR_PATTERN = Pattern.compile("(?i)" + COLOR_CHAR + "[0-9A-FK-OR]");
	public static final Pattern STRIP_MC_COLOR_PATTERN = Pattern.compile("§[0-8abcdefklmnor]");
    public static Integer getPort() {
        return RconServerYAML.getInstance().adapter().getInt("port", 25575);
    }

    public static String getPass() {
        return RconServerYAML.getInstance().adapter().getString("password", "");
    }

    public static boolean isColored() {
        return RconServerYAML.getInstance().adapter().getBoolean("colored", true);
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
		ChannelFuture future = rconServer.bind(address);
		Channel channel = future.awaitUninterruptibly().channel();
		if (!channel.isActive()) {
			stopRconListener();
		}
		ua.co.tensa.Message.info("Binding rcon to address: " + address.getHostName() + ":" + address.getPort());
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
