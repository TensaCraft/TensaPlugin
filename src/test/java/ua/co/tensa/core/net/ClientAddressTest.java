package ua.co.tensa.core.net;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class ClientAddressTest {
    @Test
    void resolvedHostnameIsNeverPrependedToTheIp() throws Exception {
        InetAddress resolved = InetAddress.getByAddress(
                "109-227-98-164.dynamic-pool.mclaut.cc", new byte[]{109, (byte) 227, 98, (byte) 164});
        InetSocketAddress address = new InetSocketAddress(resolved, 58549);

        assertThat(resolved.toString().replace("/", ""))
                .isEqualTo("109-227-98-164.dynamic-pool.mclaut.cc109.227.98.164");
        assertThat(ClientAddress.ip(address)).isEqualTo("109.227.98.164");
        assertThat(ClientAddress.ipAndPort(address)).isEqualTo("109.227.98.164:58549");
    }

    @Test
    void failedReverseLookupDoesNotDoubleTheIp() throws Exception {
        InetAddress unresolved = InetAddress.getByAddress(
                "178.216.17.177", new byte[]{(byte) 178, (byte) 216, 17, (byte) 177});

        assertThat(ClientAddress.ip(new InetSocketAddress(unresolved, 25565))).isEqualTo("178.216.17.177");
    }

    @Test
    void ipv6IsBracketedOnlyWithAPort() throws Exception {
        InetSocketAddress address = new InetSocketAddress(InetAddress.getByName("2001:db8::1"), 25565);

        assertThat(ClientAddress.ip(address)).isEqualTo("2001:db8:0:0:0:0:0:1");
        assertThat(ClientAddress.ipAndPort(address)).isEqualTo("[2001:db8:0:0:0:0:0:1]:25565");
    }

    @Test
    void missingAddressRendersEmpty() {
        assertThat(ClientAddress.ip(null)).isEmpty();
        assertThat(ClientAddress.ipAndPort(null)).isEmpty();
    }
}
