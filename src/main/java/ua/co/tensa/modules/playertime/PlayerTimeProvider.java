package ua.co.tensa.modules.playertime;

import ua.co.tensa.modules.ModuleEntry;
import ua.co.tensa.modules.ModuleProvider;
import ua.co.tensa.modules.TensaModule;

@TensaModule(id = "player-time", title = "Player Time")
public class PlayerTimeProvider implements ModuleProvider {
    @Override public String id() { return "player-time"; }
    @Override public ModuleEntry entry() { return PlayerTimeModule.ENTRY; }
}

