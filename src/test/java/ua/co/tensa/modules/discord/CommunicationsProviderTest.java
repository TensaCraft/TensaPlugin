package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;
import ua.co.tensa.modules.ModuleProvider;
import ua.co.tensa.modules.TensaModule;

import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

class CommunicationsProviderTest {
    @Test
    void neutralProviderReplacesSeparateChatAndDiscordModules() {
        CommunicationsProvider provider = new CommunicationsProvider();
        TensaModule annotation = provider.getClass().getAnnotation(TensaModule.class);

        assertThat(provider.id()).isEqualTo("communications");
        assertThat(provider.entry().id()).isEqualTo("communications");
        assertThat(provider.entry().title()).isEqualTo("Communications");
        assertThat(annotation).isNotNull();
        assertThat(annotation.defaultEnabled()).isTrue();
        assertThat(ServiceLoader.load(ModuleProvider.class).stream()
                .map(ServiceLoader.Provider::get)
                .map(ModuleProvider::id))
                .contains("communications")
                .doesNotContain("chat-manager", "discord");
    }
}
