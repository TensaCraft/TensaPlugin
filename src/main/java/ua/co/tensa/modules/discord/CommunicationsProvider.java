package ua.co.tensa.modules.discord;

import ua.co.tensa.modules.ModuleEntry;
import ua.co.tensa.modules.ModuleProvider;
import ua.co.tensa.modules.TensaModule;

@TensaModule(id = "communications", title = "Communications")
public final class CommunicationsProvider implements ModuleProvider {
    @Override
    public String id() {
        return "communications";
    }

    @Override
    public ModuleEntry entry() {
        return CommunicationsModule.ENTRY;
    }
}
