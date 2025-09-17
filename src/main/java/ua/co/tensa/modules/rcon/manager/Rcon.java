package ua.co.tensa.modules.rcon.manager;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Random;

public class Rcon {
	private static final int MIN_PORT = 1;
	private static final int MAX_PORT = 65535;

	private final Object sync;
	private final Random rand;

	private int requestId;
	private Socket socket;
	private Charset charset;

	public Rcon(String host, int port, byte[] password) throws IOException, AuthenticationException {
		this.sync = new Object();
		this.rand = new Random();
		this.charset = StandardCharsets.UTF_8;

		connect(host, port, password);
	}

	public void connect(String host, int port, byte[] password) throws IOException, AuthenticationException {
		if (host == null || host.trim().isEmpty()) {
			throw new IllegalArgumentException("Host can't be null or empty");
		}

		if (port < MIN_PORT || port > MAX_PORT) {
			throw new IllegalArgumentException("Port is out of range");
		}

        synchronized (this.sync) {
            this.requestId = this.rand.nextInt();
            this.socket = new Socket();
            this.socket.connect(new InetSocketAddress(host, port), 5000); // connect timeout
            try { this.socket.setSoTimeout(5000); } catch (Exception ignored) {}
        }

		RconPacket res = send(3, password);

		if (res.getRequestId() == -1) {
			throw new AuthenticationException("Password rejected by server");
		}
	}

	public void disconnect() throws IOException {
		synchronized (this.sync) {
			if (this.socket != null && !this.socket.isClosed()) {
				this.socket.close();
			}
		}
	}

	public String command(String payload) throws IOException {
		if (payload == null || payload.trim().isEmpty()) {
			throw new IllegalArgumentException("Payload can't be null or empty");
		}

		RconPacket response = send(2, payload.getBytes(this.charset));

		return new String(response.getPayload(), this.charset);
	}

	private RconPacket send(int type, byte[] payload) throws IOException {
		synchronized (this.sync) {
			return RconPacket.send(this, type, payload);
		}
	}

	public int getRequestId() {
		return this.requestId;
	}

	public Socket getSocket() {
		return this.socket;
	}

	public Charset getCharset() {
		return this.charset;
	}

	public void setCharset(Charset charset) {
		this.charset = charset;
	}
}
