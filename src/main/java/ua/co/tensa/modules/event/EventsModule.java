package ua.co.tensa.modules.event;

import ua.co.tensa.modules.event.data.EventsYAML;
import ua.co.tensa.modules.AbstractModule;
import ua.co.tensa.modules.ModuleEntry;

import java.util.Collections;
import java.util.List;


public class EventsModule extends org.simpleyaml.configuration.file.YamlConfiguration {

    private static final ModuleEntry IMPL = new AbstractModule(
            "events-manager", "Events Manager") {
        @Override protected void onEnable() {
            ua.co.tensa.modules.AbstractModule.ensureConfig(EventsYAML.getInstance());
            ua.co.tensa.Message.info("Events Manager module enabled");
        }
        @Override protected void onDisable() { /* stateless */ }
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
            var a = EventsYAML.getInstance().adapter();
            return a.getBoolean("events." + this.key + ".enabled", false);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        public List commands() {
            var a = EventsYAML.getInstance().adapter();
            java.util.List<Object> list = a.getList("events." + this.key + ".commands");
            return list == null ? Collections.emptyList() : list;
        }
	}
}
