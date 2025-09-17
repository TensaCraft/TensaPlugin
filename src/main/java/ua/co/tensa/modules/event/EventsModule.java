package ua.co.tensa.modules.event;

import ua.co.tensa.modules.AbstractModule;
import ua.co.tensa.modules.ModuleEntry;
import ua.co.tensa.modules.event.data.EventsConfig;

import java.util.List;


public class EventsModule extends org.simpleyaml.configuration.file.YamlConfiguration {

    private static final ModuleEntry IMPL = new AbstractModule(
            "events-manager", "Events Manager") {
        @Override protected void onEnable() {
            EventsConfig.get().reloadCfg();
            // Register event listener only when module is enabled
            ((AbstractModule) IMPL).registerListener(new EventsListener());
            // Fire server-running sequence at enable-time in case we missed initial ProxyInitializeEvent
            try { EventManager.onServerRunning(null); } catch (Throwable ignored) { }
        }
        @Override protected void onReload() { EventsConfig.get().reloadCfg(); }
        @Override protected void onDisable() { /* listeners are auto-unregistered */ }
    };
    public static final ModuleEntry ENTRY = IMPL;

    public static void enable() { IMPL.enable(); }
    public static void disable() { IMPL.disable(); }

	public enum Events {
		on_join_commands("on_join_commands"), on_leave_commands("on_leave_commands"),
		on_server_switch("on_server_switch"), on_server_kick("on_server_kick"),
		on_server_running("on_server_running"), on_server_stop("on_server_stop");

		private final String key;

		Events(String key) {
			this.key = key;
		}

        public boolean enabled() {
            EventsConfig c = EventsConfig.get();
            return switch (this) {
                case on_join_commands -> c.onJoinEnabled;
                case on_leave_commands -> c.onLeaveEnabled;
                case on_server_switch -> c.onServerSwitchEnabled;
                case on_server_kick -> c.onServerKickEnabled;
                case on_server_running -> c.onServerRunningEnabled;
                case on_server_stop -> c.onServerStopEnabled;
            };
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        public List commands() {
            EventsConfig c = EventsConfig.get();
            return switch (this) {
                case on_join_commands -> c.onJoinCommands;
                case on_leave_commands -> c.onLeaveCommands;
                case on_server_switch -> c.onServerSwitchCommands;
                case on_server_kick -> c.onServerKickCommands;
                case on_server_running -> c.onServerRunningCommands;
                case on_server_stop -> c.onServerStopCommands;
            };
        }
	}
}
