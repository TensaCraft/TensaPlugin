package ua.co.tensa.modules.runtime;

import ua.co.tensa.modules.ModuleEntry;
import ua.co.tensa.modules.ModuleProvider;
import ua.co.tensa.modules.TensaModule;

@TensaModule(id = "scheduler", title = "Task Scheduler")
public final class SchedulerProvider implements ModuleProvider {
    @Override
    public String id() {
        return "scheduler";
    }

    @Override
    public ModuleEntry entry() {
        return SchedulerModule.ENTRY;
    }
}
