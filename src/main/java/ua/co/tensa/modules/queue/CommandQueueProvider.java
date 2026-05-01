package ua.co.tensa.modules.queue;

import ua.co.tensa.modules.ModuleEntry;
import ua.co.tensa.modules.ModuleProvider;
import ua.co.tensa.modules.TensaModule;

@TensaModule(id = "command-queue", title = "Command Queue")
public final class CommandQueueProvider implements ModuleProvider {
    @Override public String id() { return "command-queue"; }
    @Override public ModuleEntry entry() { return CommandQueueModule.ENTRY; }
}
