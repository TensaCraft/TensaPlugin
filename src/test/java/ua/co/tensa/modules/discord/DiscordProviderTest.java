package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;
import ua.co.tensa.modules.ModuleProvider;
import ua.co.tensa.modules.TensaModule;

import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordProviderTest {
    @Test
    void providerIsDiscoverableAndDisabledByDefault() {
        DiscordProvider provider = new DiscordProvider();
        TensaModule annotation = provider.getClass().getAnnotation(TensaModule.class);

        assertThat(provider.id()).isEqualTo("discord");
        assertThat(provider.entry().id()).isEqualTo("discord");
        assertThat(annotation).isNotNull();
        assertThat(annotation.defaultEnabled()).isFalse();
        assertThat(ServiceLoader.load(ModuleProvider.class).stream()
                .map(ServiceLoader.Provider::get)
                .map(ModuleProvider::id))
                .contains("discord");
    }
}
