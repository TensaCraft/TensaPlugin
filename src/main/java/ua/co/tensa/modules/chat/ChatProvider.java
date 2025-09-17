package ua.co.tensa.modules.chat;

import ua.co.tensa.modules.ModuleEntry;
import ua.co.tensa.modules.ModuleProvider;
import ua.co.tensa.modules.TensaModule;

@TensaModule(id = "chat-manager", title = "Chat Manager")
public class ChatProvider implements ModuleProvider {
    @Override public String id() { return "chat-manager"; }
    @Override public ModuleEntry entry() { return ChatModule.ENTRY; }
}

