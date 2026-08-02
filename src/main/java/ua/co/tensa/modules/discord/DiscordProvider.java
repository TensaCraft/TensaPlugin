package ua.co.tensa.modules.discord;

import ua.co.tensa.modules.ModuleEntry;
import ua.co.tensa.modules.ModuleProvider;
import ua.co.tensa.modules.TensaModule;

@TensaModule(id = "discord", title = "Discord", defaultEnabled = false)
public final class DiscordProvider implements ModuleProvider {
    @Override
    public String id() {
        return "discord";
    }

    @Override
    public ModuleEntry entry() {
        return DiscordModule.ENTRY;
    }
}
