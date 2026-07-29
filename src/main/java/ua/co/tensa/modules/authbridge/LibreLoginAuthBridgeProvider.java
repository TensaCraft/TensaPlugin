package ua.co.tensa.modules.authbridge;

import ua.co.tensa.modules.ModuleEntry;
import ua.co.tensa.modules.ModuleProvider;
import ua.co.tensa.modules.TensaModule;

@TensaModule(id = "librelogin-auth-bridge", title = "LibreLogin Auth Bridge", defaultEnabled = false)
public final class LibreLoginAuthBridgeProvider implements ModuleProvider {
    @Override
    public String id() {
        return "librelogin-auth-bridge";
    }

    @Override
    public ModuleEntry entry() {
        return LibreLoginAuthBridgeModule.ENTRY;
    }
}
