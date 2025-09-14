package ua.co.tensa.modules.event.data;

import ua.co.tensa.config.data.BaseYAMLConfig;

public class EventsYAML extends BaseYAMLConfig {

    private static EventsYAML instance;

    private EventsYAML() {
        super("events.yml");
    }

    public static EventsYAML getInstance() {
        if (instance == null) {
            instance = new EventsYAML();
        }
        return instance;
    }

    @Override
    protected void populateConfigFile() {
        yamlFile.setHeader(
                "Events settings \n" +
                        "Placeholders: {player}, {server}, {fromServer}\n" +
                        "[console] - run console command\n" +
                        "[delay] (seconds) - delay seconds command"
        );

        setConfigValue("events.on_join_commands.enabled", true);
        setConfigValue("events.on_join_commands.commands", java.util.List.of(
                "[console] alert <gold>Player {player} join the game</gold>",
                "[delay] 10",
                "server vanilla"
        ));

        setConfigValue("events.on_leave_commands.enabled", true);
        setConfigValue("events.on_leave_commands.commands", java.util.List.of(
                "[console] alert <gold>Player {player} left the game</gold>"
        ));

        setConfigValue("events.on_server_switch.enabled", true);
        setConfigValue("events.on_server_switch.commands", java.util.List.of(
                "[console] alert <gold>Player {player} connected to server {server} from server {fromServer}</gold>"
        ));

        setConfigValue("events.on_server_kick.enabled", true);
        setConfigValue("events.on_server_kick.commands", java.util.List.of(
                "[console] alert <gold>Player {player} kick the server {server}</gold>",
                "[delay] 60",
                "server {server}"
        ));

        setConfigValue("events.on_server_running.enabled", true);
        setConfigValue("events.on_server_running.commands", java.util.List.of(
                "[console] alert <gold>Server {server} is running</gold>"
        ));

        setConfigValue("events.on_server_stop.enabled", true);
        setConfigValue("events.on_server_stop.commands", java.util.List.of(
                "[console] alert <gold>Server {server} is stop</gold>"
        ));
    }
}
