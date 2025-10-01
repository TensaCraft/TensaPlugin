package ua.co.tensa.modules.rcon.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;

import java.nio.ByteOrder;
import java.util.List;

public class RconFramingHandler extends ByteToMessageCodec<ByteBuf> {

	// RCON protocol maximum packet size (4096 bytes per spec)
	private static final int MAX_PACKET_SIZE = 4096;

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) {
		ctx.flush();
	}

	@SuppressWarnings("deprecation")
	@Override
	protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) {
		out.order(ByteOrder.LITTLE_ENDIAN).writeInt(msg.readableBytes());
		out.writeBytes(msg);
	}

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
		if (in.readableBytes() < 4) {
			return;
		}

		in.markReaderIndex();
		@SuppressWarnings("deprecation")
		int length = in.order(ByteOrder.LITTLE_ENDIAN).readInt();

		// DoS Protection: Validate packet size
		if (length < 0 || length > MAX_PACKET_SIZE) {
			ua.co.tensa.Message.warn("RCON received invalid packet size: " + length + " from " + ctx.channel().remoteAddress());
			ctx.close();
			return;
		}

		if (in.readableBytes() < length) {
			in.resetReaderIndex();
			return;
		}

		ByteBuf buf = ctx.alloc().buffer(length);
		in.readBytes(buf, length);
		out.add(buf);
	}
}
