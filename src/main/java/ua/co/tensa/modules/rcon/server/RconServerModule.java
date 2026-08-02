package ua.co.tensa.modules.rcon.server;

import com.velocitypowered.api.proxy.ProxyServer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import ua.co.tensa.Tensa;
import ua.co.tensa.config.model.YamlConfigPreflight;
import ua.co.tensa.modules.AbstractModule;
import ua.co.tensa.modules.ModuleEntry;
import ua.co.tensa.modules.rcon.data.RconServerConfig;
import ua.co.tensa.modules.runtime.AtomicRuntimeSlot;

import java.net.InetSocketAddress;
import java.util.regex.Pattern;

public class RconServerModule {

    private static final AtomicRuntimeSlot<Settings, ActiveRuntime> RUNTIME =
            new AtomicRuntimeSlot<>(RconServerModule::activate, RconServerModule::deactivate);

    private static final ModuleEntry IMPL = new AbstractModule(
            "rcon-server", "Rcon Server") {
        @Override protected void onEnable() { RUNTIME.start(prepare()); }
        @Override protected void onDisable() { RUNTIME.close(); }
        @Override protected void onReload() { RUNTIME.replace(prepare()); }
        @Override protected boolean restartOnReloadFailure() { return false; }
    };
    public static final ModuleEntry ENTRY = IMPL;

	private static volatile Settings currentSettings;
	public static final char COLOR_CHAR = '\u00A7';
	public static final Pattern STRIP_COLOR_PATTERN = Pattern.compile("(?i)" + COLOR_CHAR + "[0-9A-FK-OR]");
	public static final Pattern STRIP_MC_COLOR_PATTERN = Pattern.compile("§[0-8abcdefklmnor]");
    public static Integer getPort() {
        Settings settings = settings();
        return settings.port();
    }

    public static String getPass() {
        return settings().password();
    }

    public static boolean isColored() {
        return settings().colored();
    }

    public static boolean isDebugEnabled() {
        return settings().debug();
    }

    public static boolean isErrorLoggingEnabled() {
        return settings().logErrors();
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

    private static Settings prepare() {
        YamlConfigPreflight.validate(Tensa.pluginPath.resolve("rcon/rcon-server.yml"));
        RconServerConfig config = RconServerConfig.get();
        config.reloadCfg();
        if (config.port < 1 || config.port > 65_535) {
            throw new IllegalStateException("RCON listener port must be between 1 and 65535");
        }
        if (config.password == null || config.password.isBlank()) {
            throw new IllegalStateException("RCON listener password must not be blank");
        }
        return new Settings(config.port, config.password, config.colored, config.debug, config.logErrors);
    }

    private static ActiveRuntime activate(Settings settings) {
        InetSocketAddress address = new InetSocketAddress(settings.port());
        ProxyServer proxyServer = Tensa.server;
        RconServer created = new RconServer(proxyServer, settings.password());
        currentSettings = settings;
        try {
            ChannelFuture future = created.bind(address).syncUninterruptibly();
            Channel channel = future.channel();
            if (channel != null && channel.isActive()) {
                ua.co.tensa.Message.rcon("BOUND", address.getHostName() + ":" + address.getPort());
                return new ActiveRuntime(created);
            } else {
                throw new IllegalStateException("RCON listener did not become active");
            }
        } catch (Throwable t) {
            currentSettings = null;
            created.shutdown();
            throw new IllegalStateException("RCON listener bind failed: " + t.getClass().getSimpleName(), t);
        }
    }

	private static void deactivate(ActiveRuntime active) {
		if (active != null) {
			ua.co.tensa.Message.rcon("STOPPING", "Shutting down RCON listener");
			active.server().shutdown();
		}
		currentSettings = null;
	}

    private static Settings settings() {
        Settings active = currentSettings;
        if (active != null) return active;
        RconServerConfig config = RconServerConfig.get();
        return new Settings(config.port, config.password, config.colored, config.debug, config.logErrors);
    }

    public static void enable() { IMPL.enable(); }
    public static void disable() { IMPL.disable(); }

    private record Settings(int port, String password, boolean colored, boolean debug, boolean logErrors) {
    }

    private record ActiveRuntime(RconServer server) {
    }

}
