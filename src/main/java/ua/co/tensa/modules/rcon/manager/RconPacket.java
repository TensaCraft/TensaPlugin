package ua.co.tensa.modules.rcon.manager;

import java.io.*;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class RconPacket {
	public static final int SERVERDATA_RESPONSE_VALUE = 0;
	public static final int SERVERDATA_EXECCOMMAND = 2;
	public static final int SERVERDATA_AUTH = 3;
	public static final int SERVERDATA_AUTH_RESPONSE = 2;

	private final int requestId;
	private final int type;
	private final byte[] payload;

	private RconPacket(int requestId, int type, byte[] payload) {
		this.requestId = requestId;
		this.type = type;
		this.payload = payload;
	}

	public int getRequestId() {
		return this.requestId;
	}

	public int getType() {
		return this.type;
	}

	public byte[] getPayload() {
		return this.payload;
	}

	protected static RconPacket send(Rcon rcon, int type, byte[] payload) throws IOException {
		try {
			write(rcon.getSocket().getOutputStream(), rcon.getRequestId(), type, payload);
		} catch (SocketException se) {
			rcon.getSocket().close();
			throw se;
		}

		return readResponse(rcon);
	}

	private static void write(OutputStream out, int requestId, int type, byte[] payload) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(4 + 4 + 4 + payload.length + 2);
		buffer.order(ByteOrder.LITTLE_ENDIAN);

		buffer.putInt(4 + 4 + payload.length + 2); // Packet size
		buffer.putInt(requestId);
		buffer.putInt(type);
		buffer.put(payload);
		buffer.put((byte) 0); // String null terminator
		buffer.put((byte) 0); // Empty string null terminator

		out.write(buffer.array());
		out.flush();
	}

	private static RconPacket readResponse(Rcon rcon) throws IOException {
		InputStream in = rcon.getSocket().getInputStream();
		DataInputStream dis = new DataInputStream(in);

		ByteArrayOutputStream payloadStream = new ByteArrayOutputStream();
		int responseRequestId = -1;
		int responseType = -1;

        while (true) {
            int packetSize;
            try {
                packetSize = Integer.reverseBytes(dis.readInt());
            } catch (IOException e) {
                throw new IOException("Failed to read packet size", e);
            }

			byte[] packetData = new byte[packetSize];
			dis.readFully(packetData);

			ByteBuffer packetBuffer = ByteBuffer.wrap(packetData);
			packetBuffer.order(ByteOrder.LITTLE_ENDIAN);

			int requestId = packetBuffer.getInt();
			int type = packetBuffer.getInt();

			byte[] payload = new byte[packetSize - 8 - 2]; // Exclude requestId, type, and two null bytes
			packetBuffer.get(payload);

			// Read the two null bytes
			packetBuffer.get(); // Null byte
			packetBuffer.get(); // Null byte

			if (responseRequestId == -1) {
				responseRequestId = requestId;
			}

			if (responseType == -1) {
				responseType = type;
			}

			payloadStream.write(payload);

            // Stop when packet smaller than maximum chunk size used by servers
            if (packetSize < 4096) {
                break;
            }
        }

		byte[] fullPayload = payloadStream.toByteArray();

		return new RconPacket(responseRequestId, responseType, fullPayload);
	}
}
