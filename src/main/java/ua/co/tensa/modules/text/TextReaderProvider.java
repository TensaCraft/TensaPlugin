package ua.co.tensa.modules.text;

import ua.co.tensa.modules.ModuleEntry;
import ua.co.tensa.modules.ModuleProvider;
import ua.co.tensa.modules.TensaModule;

@TensaModule(id = "text-reader", title = "Text Reader")
public class TextReaderProvider implements ModuleProvider {
    @Override public String id() { return "text-reader"; }
    @Override public ModuleEntry entry() { return TextReaderModule.ENTRY; }
}

