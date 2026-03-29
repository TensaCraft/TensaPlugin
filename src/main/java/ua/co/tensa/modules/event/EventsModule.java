package ua.co.tensa.modules.event;

import ua.co.tensa.modules.AbstractModule;
import ua.co.tensa.modules.ModuleEntry;
import ua.co.tensa.modules.event.data.EventsConfig;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;


public class EventsModule {

    private static final ModuleEntry IMPL = new AbstractModule(
            "events-manager", "Events Manager") {
        @Override protected void onEnable() {
            EventsConfig.get().reloadCfg();
            // Register event listener only when module is enabled
            ((AbstractModule) IMPL).registerListener(new EventsListener());
            // Fire server-running sequence at enable-time in case we missed initial ProxyInitializeEvent
            try { EventManager.onServerRunning(); } catch (Throwable ignored) { }
        }
        @Override protected void onReload() { EventsConfig.get().reloadCfg(); }
        @Override protected void onDisable() { /* listeners are auto-unregistered */ }
    };
    public static final ModuleEntry ENTRY = IMPL;

    public static void enable() { IMPL.enable(); }
    public static void disable() { IMPL.disable(); }

	public enum Events {
		on_pre_login_commands("on_pre_login_commands", c -> c.onPreLoginEnabled, c -> c.onPreLoginCommands),
        on_login_commands("on_login_commands", c -> c.onLoginEnabled, c -> c.onLoginCommands),
        on_join_commands("on_join_commands", c -> c.onJoinEnabled, c -> c.onJoinCommands),
        on_leave_commands("on_leave_commands", c -> c.onLeaveEnabled, c -> c.onLeaveCommands),
        on_initial_server_commands("on_initial_server_commands", c -> c.onInitialServerEnabled, c -> c.onInitialServerCommands),
        on_server_pre_connect("on_server_pre_connect", c -> c.onServerPreConnectEnabled, c -> c.onServerPreConnectCommands),
        on_server_switch("on_server_switch", c -> c.onServerSwitchEnabled, c -> c.onServerSwitchCommands),
        on_server_post_connect("on_server_post_connect", c -> c.onServerPostConnectEnabled, c -> c.onServerPostConnectCommands),
        on_server_kick("on_server_kick", c -> c.onServerKickEnabled, c -> c.onServerKickCommands),
        on_server_running("on_server_running", c -> c.onServerRunningEnabled, c -> c.onServerRunningCommands),
        on_proxy_reload("on_proxy_reload", c -> c.onProxyReloadEnabled, c -> c.onProxyReloadCommands),
        on_listener_bound("on_listener_bound", c -> c.onListenerBoundEnabled, c -> c.onListenerBoundCommands),
        on_listener_close("on_listener_close", c -> c.onListenerCloseEnabled, c -> c.onListenerCloseCommands),
        on_server_pre_stop("on_server_pre_stop", c -> c.onServerPreStopEnabled, c -> c.onServerPreStopCommands),
        on_server_stop("on_server_stop", c -> c.onServerStopEnabled, c -> c.onServerStopCommands);

		private final String key;
        private final Predicate<EventsConfig> enabledResolver;
        private final Function<EventsConfig, List<String>> commandsResolver;

		Events(String key, Predicate<EventsConfig> enabledResolver, Function<EventsConfig, List<String>> commandsResolver) {
			this.key = key;
            this.enabledResolver = enabledResolver;
            this.commandsResolver = commandsResolver;
		}

        public boolean enabled() {
            return enabledResolver.test(EventsConfig.get());
        }

        public List<String> commands() {
            List<String> commands = commandsResolver.apply(EventsConfig.get());
            return commands == null ? List.of() : List.copyOf(commands);
        }
	}
}
