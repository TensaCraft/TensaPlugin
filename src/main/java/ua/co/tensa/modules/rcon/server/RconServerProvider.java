package ua.co.tensa.modules.rcon.server;

import ua.co.tensa.modules.ModuleEntry;
import ua.co.tensa.modules.ModuleProvider;
import ua.co.tensa.modules.TensaModule;

@TensaModule(id = "rcon-server", title = "Rcon Server")
public class RconServerProvider implements ModuleProvider {
    @Override public String id() { return "rcon-server"; }
    @Override public ModuleEntry entry() { return RconServerModule.ENTRY; }
}

