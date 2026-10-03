package ua.co.tensa.modules.rcon.manager;

import java.io.*;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class RconPacket {
	private static final int MIN_PACKET_SIZE = 10;
	private static final int MAX_PACKET_SIZE = 4 * 1024 * 1024;
	private static final int MAX_RESPONSE_SIZE = 16 * 1024 * 1024;
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

		if (type == SERVERDATA_AUTH) {
			InputStream input = rcon.getSocket().getInputStream();
			RconPacket response = readResponse(input);
			if (response.type == SERVERDATA_RESPONSE_VALUE && response.payload.length == 0) {
				response = readResponse(input);
			}
			if (response.type != SERVERDATA_AUTH_RESPONSE
					|| response.requestId != -1 && response.requestId != rcon.getRequestId()) {
				throw new IOException("Invalid RCON authentication response");
			}
			return response;
		}
		return readResponse(rcon);
	}

	private static void write(OutputStream out, int requestId, int type, byte[] payload) throws IOException {
		if (payload == null || payload.length > MAX_PACKET_SIZE - MIN_PACKET_SIZE) {
			throw new IOException("RCON request payload exceeds the safe size limit");
		}
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
		return readResponse(rcon.getSocket().getInputStream());
	}

	static RconPacket readResponse(InputStream in) throws IOException {
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
			if (packetSize < MIN_PACKET_SIZE || packetSize > MAX_PACKET_SIZE) {
				throw new IOException("RCON response packet size is outside safe bounds");
			}

			byte[] packetData = new byte[packetSize];
			dis.readFully(packetData);

			ByteBuffer packetBuffer = ByteBuffer.wrap(packetData);
			packetBuffer.order(ByteOrder.LITTLE_ENDIAN);

			int requestId = packetBuffer.getInt();
			int type = packetBuffer.getInt();

			byte[] payload = new byte[packetSize - 8 - 2]; // Exclude requestId, type, and two null bytes
			if ((long) payloadStream.size() + payload.length > MAX_RESPONSE_SIZE) {
				throw new IOException("RCON aggregate response exceeds the safe size limit");
			}
			packetBuffer.get(payload);

			// Read the two null bytes
			if (packetBuffer.get() != 0 || packetBuffer.get() != 0) {
				throw new IOException("RCON response has invalid string terminators");
			}

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
