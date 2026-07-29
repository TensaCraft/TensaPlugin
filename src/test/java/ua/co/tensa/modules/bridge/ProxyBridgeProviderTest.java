package ua.co.tensa.modules.bridge;

import org.junit.jupiter.api.Test;
import ua.co.tensa.modules.TensaModule;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyBridgeProviderTest {

    @Test
    void proxyBridgeProviderUsesProxyBridgeIdentity() {
        ProxyBridgeProvider provider = new ProxyBridgeProvider();
        TensaModule annotation = provider.getClass().getAnnotation(TensaModule.class);

        assertThat(provider.id()).isEqualTo("proxy-bridge");
        assertThat(provider.entry().id()).isEqualTo("proxy-bridge");
        assertThat(provider.entry().title()).isEqualTo("ProxyBridge");
        assertThat(annotation).isNotNull();
        assertThat(annotation.defaultEnabled()).isFalse();
    }
}
