package ua.co.tensa.modules.event.data;

import ua.co.tensa.config.model.ConfigBase;
import ua.co.tensa.config.model.ann.CfgKey;

import java.util.ArrayList;
import java.util.List;

public class EventsConfig extends ConfigBase {
    private static EventsConfig instance;

    private static final String COMMAND_HINT = "Use [console] to run a command as the proxy console and [delay] <seconds> to postpone execution.";
    private static final String PLAYER_PLACEHOLDERS = "Placeholders: {event}, {player}, {uuid}, {ip}, {host}, {port}, {address}, {protocol}, {intent}, {server}. " + COMMAND_HINT;
    private static final String FIRST_JOIN_PLACEHOLDERS = "Placeholders: {event}, {player}, {uuid}, {ip}, {host}, {port}, {address}, {protocol}, {intent}, {server}, {firstJoinAt}. " + COMMAND_HINT;
    private static final String SERVER_PLACEHOLDERS = "Placeholders: {event}, {player}, {uuid}, {ip}, {host}, {port}, {address}, {protocol}, {intent}, {server}, {fromServer}, {toServer}, {originalServer}, {initialServer}. " + COMMAND_HINT;
    private static final String PROXY_PLACEHOLDERS = "Placeholders: {event}. " + COMMAND_HINT;
    private static final String LISTENER_PLACEHOLDERS = "Placeholders: {event}, {listener}, {address}, {host}, {port}. " + COMMAND_HINT;
    private static final String COMMANDS_COMMENT = "Commands executed in order for this event. Supports [console] and [delay] <seconds> prefixes.";

    @CfgKey(value = "events.on_pre_login_commands.enabled", comment = "Runs before proxy authentication completes. " + PLAYER_PLACEHOLDERS)
    public boolean onPreLoginEnabled = false;
    @CfgKey(value = "events.on_pre_login_commands.commands", comment = COMMANDS_COMMENT)
    public List<String> onPreLoginCommands = new ArrayList<>();

    @CfgKey(value = "events.on_login_commands.enabled", comment = "Runs after authentication but before the initial backend connection. " + PLAYER_PLACEHOLDERS)
    public boolean onLoginEnabled = false;
    @CfgKey(value = "events.on_login_commands.commands", comment = COMMANDS_COMMENT)
    public List<String> onLoginCommands = new ArrayList<>();

    @CfgKey(value = "events.on_join_commands.enabled", comment = "Runs after the player joins the proxy. " + PLAYER_PLACEHOLDERS)
    public boolean onJoinEnabled = false;
    @CfgKey(value = "events.on_join_commands.commands", comment = COMMANDS_COMMENT)
    public List<String> onJoinCommands = new ArrayList<>();

    @CfgKey(value = "events.on_first_join_commands.enabled", comment = "Runs only once for each player, the first time they join the proxy according to core user storage. " + FIRST_JOIN_PLACEHOLDERS)
    public boolean onFirstJoinEnabled = false;
    @CfgKey(value = "events.on_first_join_commands.commands", comment = COMMANDS_COMMENT)
    public List<String> onFirstJoinCommands = new ArrayList<>();

    @CfgKey(value = "events.on_leave_commands.enabled", comment = "Runs when a player disconnects from the proxy. " + PLAYER_PLACEHOLDERS)
    public boolean onLeaveEnabled = false;
    @CfgKey(value = "events.on_leave_commands.commands", comment = COMMANDS_COMMENT)
    public List<String> onLeaveCommands = new ArrayList<>();

    @CfgKey(value = "events.on_initial_server_commands.enabled", comment = "Runs when Velocity chooses the player's first backend server. " + SERVER_PLACEHOLDERS)
    public boolean onInitialServerEnabled = false;
    @CfgKey(value = "events.on_initial_server_commands.commands", comment = COMMANDS_COMMENT)
    public List<String> onInitialServerCommands = new ArrayList<>();

    @CfgKey(value = "events.on_server_pre_connect.enabled", comment = "Runs before a player connects to a backend server. " + SERVER_PLACEHOLDERS)
    public boolean onServerPreConnectEnabled = false;
    @CfgKey(value = "events.on_server_pre_connect.commands", comment = COMMANDS_COMMENT)
    public List<String> onServerPreConnectCommands = new ArrayList<>();

    @CfgKey(value = "events.on_server_switch.enabled", comment = "Runs after a player switches from one backend server to another. " + SERVER_PLACEHOLDERS)
    public boolean onServerSwitchEnabled = false;
    @CfgKey(value = "events.on_server_switch.commands", comment = COMMANDS_COMMENT)
    public List<String> onServerSwitchCommands = new ArrayList<>();

    @CfgKey(value = "events.on_server_post_connect.enabled", comment = "Runs after a player completes a backend connection. " + SERVER_PLACEHOLDERS)
    public boolean onServerPostConnectEnabled = false;
    @CfgKey(value = "events.on_server_post_connect.commands", comment = COMMANDS_COMMENT)
    public List<String> onServerPostConnectCommands = new ArrayList<>();

    @CfgKey(value = "events.on_server_kick.enabled", comment = "Runs when a player is kicked from a backend server. " + SERVER_PLACEHOLDERS)
    public boolean onServerKickEnabled = false;
    @CfgKey(value = "events.on_server_kick.commands", comment = COMMANDS_COMMENT)
    public List<String> onServerKickCommands = new ArrayList<>();

    @CfgKey(value = "events.on_server_running.enabled", comment = "Runs when the proxy finishes initialization. " + PROXY_PLACEHOLDERS)
    public boolean onServerRunningEnabled = false;
    @CfgKey(value = "events.on_server_running.commands", comment = COMMANDS_COMMENT)
    public List<String> onServerRunningCommands = new ArrayList<>();

    @CfgKey(value = "events.on_proxy_reload.enabled", comment = "Runs when the proxy receives /velocity reload. " + PROXY_PLACEHOLDERS)
    public boolean onProxyReloadEnabled = false;
    @CfgKey(value = "events.on_proxy_reload.commands", comment = COMMANDS_COMMENT)
    public List<String> onProxyReloadCommands = new ArrayList<>();

    @CfgKey(value = "events.on_listener_bound.enabled", comment = "Runs when a proxy listener starts accepting connections. " + LISTENER_PLACEHOLDERS)
    public boolean onListenerBoundEnabled = false;
    @CfgKey(value = "events.on_listener_bound.commands", comment = COMMANDS_COMMENT)
    public List<String> onListenerBoundCommands = new ArrayList<>();

    @CfgKey(value = "events.on_listener_close.enabled", comment = "Runs before a proxy listener stops accepting connections. " + LISTENER_PLACEHOLDERS)
    public boolean onListenerCloseEnabled = false;
    @CfgKey(value = "events.on_listener_close.commands", comment = COMMANDS_COMMENT)
    public List<String> onListenerCloseCommands = new ArrayList<>();

    @CfgKey(value = "events.on_server_pre_stop.enabled", comment = "Runs before the proxy shutdown sequence completes. " + PROXY_PLACEHOLDERS)
    public boolean onServerPreStopEnabled = false;
    @CfgKey(value = "events.on_server_pre_stop.commands", comment = COMMANDS_COMMENT)
    public List<String> onServerPreStopCommands = new ArrayList<>();

    @CfgKey(value = "events.on_server_stop.enabled", comment = "Runs when the proxy shutdown sequence finishes. " + PROXY_PLACEHOLDERS)
    public boolean onServerStopEnabled = false;
    @CfgKey(value = "events.on_server_stop.commands", comment = COMMANDS_COMMENT)
    public List<String> onServerStopCommands = new ArrayList<>();

    private EventsConfig() { super("events.yml"); }
    public static synchronized EventsConfig get() { if (instance == null) instance = new EventsConfig(); return instance; }
}

