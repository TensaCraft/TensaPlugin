package ua.co.tensa.modules.rcon.manager;

import ua.co.tensa.modules.ModuleEntry;
import ua.co.tensa.modules.ModuleProvider;
import ua.co.tensa.modules.TensaModule;

@TensaModule(id = "rcon-manager", title = "Rcon Manager")
public class RconManagerProvider implements ModuleProvider {
    @Override public String id() { return "rcon-manager"; }
    @Override public ModuleEntry entry() { return RconManagerModule.ENTRY; }
}

