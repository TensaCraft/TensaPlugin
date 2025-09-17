package ua.co.tensa.modules.meta;

import ua.co.tensa.modules.ModuleEntry;
import ua.co.tensa.modules.ModuleProvider;
import ua.co.tensa.modules.TensaModule;

@TensaModule(id = "user-meta", title = "User Meta")
public class UserMetaProvider implements ModuleProvider {
    @Override public String id() { return "user-meta"; }
    @Override public ModuleEntry entry() { return UserMetaModule.ENTRY; }
}

