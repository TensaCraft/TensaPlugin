package ua.co.tensa.modules.requests;

import ua.co.tensa.modules.ModuleEntry;
import ua.co.tensa.modules.ModuleProvider;
import ua.co.tensa.modules.TensaModule;

@TensaModule(id = "requests", title = "Requests")
public class RequestsProvider implements ModuleProvider {
    @Override public String id() { return "requests"; }
    @Override public ModuleEntry entry() { return RequestsModule.ENTRY; }
}

