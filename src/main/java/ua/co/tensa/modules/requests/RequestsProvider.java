package ua.co.tensa.modules.requests;

import ua.co.tensa.modules.ModuleEntry;
import ua.co.tensa.modules.ModuleProvider;
import ua.co.tensa.modules.TensaModule;

@TensaModule(id = "request-module", title = "Requests")
public class RequestsProvider implements ModuleProvider {
    @Override public String id() { return "request-module"; }
    @Override public ModuleEntry entry() { return RequestsModule.ENTRY; }
}

