package ua.co.tensa.modules.rcon.server;

import com.velocitypowered.api.proxy.ProxyServer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;

import java.net.SocketAddress;

public class RconServer {

	private final ProxyServer server;

	private final ServerBootstrap bootstrap = new ServerBootstrap();
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;

	public RconServer(ProxyServer server, final String password) {
        this.server = server;

        int workers = Math.max(2, Runtime.getRuntime().availableProcessors());
        this.bossGroup = new NioEventLoopGroup(1);
        this.workerGroup = new NioEventLoopGroup(workers);

        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_REUSEADDR, true)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) {
                        var p = ch.pipeline();
                        // Close idle connections to avoid hanging sockets
                        p.addLast("idle", new IdleStateHandler(90, 0, 0));
                        p.addLast("frame", new RconFramingHandler());
                        p.addLast("rcon", new RconHandler(RconServer.this, password));
                    }
                });
    }

	public ChannelFuture bind(final SocketAddress address) {
		return bootstrap.bind(address);
	}

    public void shutdown() {
        try {
            workerGroup.shutdownGracefully().syncUninterruptibly();
        } catch (Throwable e) {
            ua.co.tensa.Message.debug("Worker group shutdown interrupted: " + e.getMessage());
        }
        try {
            bossGroup.shutdownGracefully().syncUninterruptibly();
        } catch (Throwable e) {
            ua.co.tensa.Message.debug("Boss group shutdown interrupted: " + e.getMessage());
        }
    }

	public ProxyServer getServer() {
		return server;
	}
}
