package ua.co.tensa.modules.event.data;

import ua.co.tensa.config.model.ConfigBase;
import ua.co.tensa.config.model.ann.CfgKey;

import java.util.ArrayList;
import java.util.List;

public class EventsConfig extends ConfigBase {
    private static EventsConfig instance;

    @CfgKey("events.on_join_commands.enabled")
    public boolean onJoinEnabled = false;
    @CfgKey("events.on_join_commands.commands")
    public List<String> onJoinCommands = new ArrayList<>();

    @CfgKey("events.on_leave_commands.enabled")
    public boolean onLeaveEnabled = false;
    @CfgKey("events.on_leave_commands.commands")
    public List<String> onLeaveCommands = new ArrayList<>();

    @CfgKey("events.on_server_switch.enabled")
    public boolean onServerSwitchEnabled = false;
    @CfgKey("events.on_server_switch.commands")
    public List<String> onServerSwitchCommands = new ArrayList<>();

    @CfgKey("events.on_server_kick.enabled")
    public boolean onServerKickEnabled = false;
    @CfgKey("events.on_server_kick.commands")
    public List<String> onServerKickCommands = new ArrayList<>();

    @CfgKey("events.on_server_running.enabled")
    public boolean onServerRunningEnabled = false;
    @CfgKey("events.on_server_running.commands")
    public List<String> onServerRunningCommands = new ArrayList<>();

    @CfgKey("events.on_server_stop.enabled")
    public boolean onServerStopEnabled = false;
    @CfgKey("events.on_server_stop.commands")
    public List<String> onServerStopCommands = new ArrayList<>();

    private EventsConfig() { super("events.yml"); }
    public static synchronized EventsConfig get() { if (instance == null) instance = new EventsConfig(); return instance; }
}

