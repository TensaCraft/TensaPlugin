package ua.co.tensa.modules.event;

import ua.co.tensa.modules.ModuleEntry;
import ua.co.tensa.modules.ModuleProvider;
import ua.co.tensa.modules.TensaModule;

@TensaModule(id = "events-manager", title = "Events Manager")
public class EventsProvider implements ModuleProvider {
    @Override public String id() { return "events-manager"; }
    @Override public ModuleEntry entry() { return EventsModule.ENTRY; }
}

