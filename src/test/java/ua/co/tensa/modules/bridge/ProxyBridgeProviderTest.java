package ua.co.tensa.modules.bridge;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyBridgeProviderTest {

    @Test
    void proxyBridgeProviderUsesProxyBridgeIdentity() {
        ProxyBridgeProvider provider = new ProxyBridgeProvider();

        assertThat(provider.id()).isEqualTo("proxy-bridge");
        assertThat(provider.entry().id()).isEqualTo("proxy-bridge");
        assertThat(provider.entry().title()).isEqualTo("ProxyBridge");
    }
}
