package ua.co.tensa.modules.authbridge;

import org.junit.jupiter.api.Test;
import ua.co.tensa.modules.ModuleProvider;
import ua.co.tensa.modules.TensaModule;

import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

class LibreLoginAuthBridgeProviderTest {
    @Test
    void providerIsDiscoverableAndDisabledByDefault() {
        LibreLoginAuthBridgeProvider provider = new LibreLoginAuthBridgeProvider();
        TensaModule annotation = provider.getClass().getAnnotation(TensaModule.class);

        assertThat(provider.id()).isEqualTo("librelogin-auth-bridge");
        assertThat(provider.entry().id()).isEqualTo("librelogin-auth-bridge");
        assertThat(annotation).isNotNull();
        assertThat(annotation.defaultEnabled()).isFalse();
    }

    @Test
    void providerIsRegisteredWithServiceLoader() {
        assertThat(ServiceLoader.load(ModuleProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .map(ModuleProvider::id))
                .contains("librelogin-auth-bridge");
    }
}
