package ua.co.tensa.modules.bridge;

import ua.co.tensa.modules.ModuleEntry;
import ua.co.tensa.modules.ModuleProvider;
import ua.co.tensa.modules.TensaModule;

@TensaModule(id = "pm-bridge", title = "PluginMessage Bridge", defaultEnabled = false)
public class PMBridgeProvider implements ModuleProvider {
    @Override public String id() { return "pm-bridge"; }
    @Override public ModuleEntry entry() { return PMBridgeModule.ENTRY; }
}
