package ua.co.tensa.modules.event;

import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.proxy.Player;
import ua.co.tensa.Tensa;
import ua.co.tensa.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static ua.co.tensa.modules.event.EventsModule.Events.*;

public class EventManager {

    private static boolean isEnabled() {
        try { return Tensa.config == null || !Tensa.config.isModuleEnabled("events-manager"); } catch (Throwable ignored) { return true; }
    }
    private static final String DELAY = "[delay]";
    private static final String CONSOLE = "[console]";

    public static void reload() { /* dynamic check via isEnabled() */ }

    private static void sendCommand(Player player, String command, boolean console) {
        if (console) {
            Util.executeCommand(command);
        } else {
            Util.executeCommand(player, command);
        }
    }

    private static List<String> commandsPrepare(List<String> commands, String player, String server, String preServer) {
        List<String> out = new ArrayList<>();
        java.util.Map<String,String> ctx = new java.util.HashMap<>();
        ctx.put("player", player);
        ctx.put("server", server);
        ctx.put("fromServer", preServer);
        for (Object command : commands) {
            String templ = String.valueOf(command);
            out.add(ua.co.tensa.Message.renderTemplateString(templ, ctx));
        }
        return out;
    }

    private static void runnable(Player player, List<String> commands, String currentServerName, String preServer) {
        AtomicLong delayAccum = new AtomicLong(0);
        for (String command : commandsPrepare(commands, player.getUsername(), currentServerName, preServer)) {
            if (command.contains(DELAY)) {
                String raw = command.replace(DELAY, "").trim();
                try {
                    long add = Long.parseLong(raw);
                    delayAccum.addAndGet(add);
                } catch (NumberFormatException e) {
                    ua.co.tensa.Message.warn("Events: invalid [delay] value '" + raw + "' — skipping");
                }
                continue;
            }
            final String cmd = command.contains(CONSOLE) ? command.replace(CONSOLE, "").trim() : command;
            final boolean asConsole = command.contains(CONSOLE);
            Tensa.server.getScheduler().buildTask(Tensa.pluginContainer, () -> sendCommand(player, cmd, asConsole))
                    .delay(delayAccum.get(), TimeUnit.SECONDS)
                    .schedule();
        }
    }

    private static void executeCommands(List<String> commands) {
        AtomicLong delayAccum = new AtomicLong(0);
        for (String command : commandsPrepare(commands, "", "", "")) {
            if (command.contains(DELAY)) {
                String raw = command.replace(DELAY, "").trim();
                try {
                    long add = Long.parseLong(raw);
                    delayAccum.addAndGet(add);
                } catch (NumberFormatException e) {
                    ua.co.tensa.Message.warn("Events: invalid [delay] value '" + raw + "' — skipping");
                }
                continue;
            }
            final String cmd = command.replace(CONSOLE, "").trim();
            Tensa.server.getScheduler().buildTask(Tensa.pluginContainer, () -> Util.executeCommand(cmd))
                    .delay(delayAccum.get(), TimeUnit.SECONDS)
                    .schedule();
        }
    }

    private static String getCurrentServerName(Player player) {
        return player.getCurrentServer().map(serverConnection -> serverConnection.getServerInfo().getName()).orElse("");
    }

    @SuppressWarnings("unchecked")
    public static void onPlayerJoin(PostLoginEvent event) {
        if (isEnabled() || !on_join_commands.enabled()) {
            return;
        }
        runnable(event.getPlayer(), on_join_commands.commands(), getCurrentServerName(event.getPlayer()), "");
    }

    @SuppressWarnings("unchecked")
    public static void onPlayerLeave(DisconnectEvent event) {
        if (isEnabled() || !on_leave_commands.enabled()) {
            return;
        }
        runnable(event.getPlayer(), on_leave_commands.commands(), getCurrentServerName(event.getPlayer()), "");
    }

    @SuppressWarnings("unchecked")
    public static void onPlayerKick(KickedFromServerEvent event) {
        if (isEnabled() || !on_server_kick.enabled()) {
            return;
        }
        runnable(event.getPlayer(), on_server_kick.commands(), event.getServer().getServerInfo().getName(), "");
    }

    @SuppressWarnings("unchecked")
    public static void onServerSwitch(ServerConnectedEvent event) {
        if (isEnabled() || !on_server_switch.enabled() || event.getPreviousServer().isEmpty()) {
            return;
        }
        String currentServerName = event.getServer().getServerInfo().getName();
        String preServer = event.getPreviousServer().map(serverConnection -> serverConnection.getServerInfo().getName())
                .orElse("");
        runnable(event.getPlayer(), on_server_switch.commands(), currentServerName, preServer);
    }

    @SuppressWarnings("unchecked")
    public static void onServerRunning(ProxyInitializeEvent event) {
        if (isEnabled() || !on_server_running.enabled()) {
            return;
        }
        executeCommands(on_server_running.commands());
    }

    @SuppressWarnings("unchecked")
    public static void onServerStop(ProxyShutdownEvent event) {
        if (isEnabled() || !on_server_stop.enabled()) {
            return;
        }
        executeCommands(on_server_stop.commands());
    }

}
