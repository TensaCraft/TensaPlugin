package ua.co.tensa.modules.bridge;

import ua.co.tensa.modules.ModuleEntry;
import ua.co.tensa.modules.ModuleProvider;
import ua.co.tensa.modules.TensaModule;

@TensaModule(id = "proxy-bridge", title = "ProxyBridge", defaultEnabled = false)
public class ProxyBridgeProvider implements ModuleProvider {
    @Override public String id() { return "proxy-bridge"; }
    @Override public ModuleEntry entry() { return ProxyBridgeModule.ENTRY; }
}
