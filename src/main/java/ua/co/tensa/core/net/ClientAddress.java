package ua.co.tensa.core.net;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * Literal client IP rendering. {@code InetAddress#toString()} yields {@code hostname/ip} and
 * {@code InetSocketAddress#getHostString()} yields the PTR name once anything (Velocity's own
 * connection logging included) has resolved it, so neither is safe to store or send as an IP.
 */
public final class ClientAddress {

    private ClientAddress() {
    }

    /** The literal IP of {@code address}, or {@code ""} when there is none. */
    public static String ip(SocketAddress address) {
        if (address instanceof InetSocketAddress inet) {
            return inet.getAddress() == null ? inet.getHostString() : inet.getAddress().getHostAddress();
        }
        return address == null ? "" : address.toString();
    }

    /** {@code ip:port}, with IPv6 literals bracketed. */
    public static String ipAndPort(SocketAddress address) {
        String ip = ip(address);
        if (!(address instanceof InetSocketAddress inet)) {
            return ip;
        }
        return (ip.indexOf(':') >= 0 ? "[" + ip + "]" : ip) + ":" + inet.getPort();
    }
}
