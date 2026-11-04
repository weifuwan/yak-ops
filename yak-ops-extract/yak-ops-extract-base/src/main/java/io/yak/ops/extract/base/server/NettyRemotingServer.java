package io.yak.ops.extract.base.server;

import io.yak.ops.common.thread.ThreadUtils;
import io.yak.ops.extract.base.config.NettyServerConfig;
import io.yak.ops.extract.base.exception.RemoteException;
import io.yak.ops.extract.base.protocal.TransporterDecoder;
import io.yak.ops.extract.base.protocal.TransporterEncoder;
import io.yak.ops.extract.base.utils.NettyUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * remoting netty server
 */
@Slf4j
class NettyRemotingServer {

    private Channel serverBootstrapChannel;

    @Getter
    private final String serverName;

    @Getter
    private final ExecutorService methodInvokerExecutor;

    private final EventLoopGroup bossGroup;

    private final EventLoopGroup workGroup;

    private final NettyServerConfig serverConfig;

    private final JdkDynamicServerHandler channelHandler;

    private final AtomicBoolean isStarted = new AtomicBoolean(false);

    NettyRemotingServer(final NettyServerConfig serverConfig) {
        this.serverConfig = serverConfig;
        this.serverName = serverConfig.getServerName();
        this.methodInvokerExecutor = ThreadUtils.newDaemonFixedThreadExecutor(
                serverName + "-methodInvoker-%d", Runtime.getRuntime().availableProcessors() * 2 + 1);
        this.channelHandler = new JdkDynamicServerHandler(methodInvokerExecutor);
        ThreadFactory bossThreadFactory =
                ThreadUtils.newDaemonThreadFactory(serverName + "-boss-%d");
        ThreadFactory workerThreadFactory =
                ThreadUtils.newDaemonThreadFactory(serverName + "-worker-%d");
        if (Epoll.isAvailable()) {
            this.bossGroup = new EpollEventLoopGroup(1, bossThreadFactory);
            this.workGroup = new EpollEventLoopGroup(serverConfig.getWorkerThread(), workerThreadFactory);
        } else {
            this.bossGroup = new NioEventLoopGroup(1, bossThreadFactory);
            this.workGroup = new NioEventLoopGroup(serverConfig.getWorkerThread(), workerThreadFactory);
        }
    }

    void start() {
        if (isStarted.compareAndSet(false, true)) {
            ServerBootstrap serverBootstrap = new ServerBootstrap()
                    .group(this.bossGroup, this.workGroup)
                    .channel(NettyUtils.getServerSocketChannelClass())
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .option(ChannelOption.SO_BACKLOG, serverConfig.getSoBacklog())
                    .childOption(ChannelOption.SO_KEEPALIVE, serverConfig.isSoKeepalive())
                    .childOption(ChannelOption.TCP_NODELAY, serverConfig.isTcpNoDelay())
                    .childOption(ChannelOption.SO_SNDBUF, serverConfig.getSendBufferSize())
                    .childOption(ChannelOption.SO_RCVBUF, serverConfig.getReceiveBufferSize())
                    .childHandler(new ChannelInitializer<SocketChannel>() {

                        @Override
                        protected void initChannel(SocketChannel ch) {
                            initNettyChannel(ch);
                        }
                    });

            try {
                final ChannelFuture channelFuture = serverBootstrap.bind(serverConfig.getListenPort()).sync();
                if (channelFuture.isSuccess()) {
                    log.info("{} bind success at port: {}", serverConfig.getServerName(), serverConfig.getListenPort());
                    this.serverBootstrapChannel = channelFuture.channel();
                } else {
                    throw new RemoteException(
                            String.format("%s bind %s fail", serverConfig.getServerName(),
                                    serverConfig.getListenPort()),
                            channelFuture.cause());
                }
            } catch (InterruptedException it) {
                ThreadUtils.rethrowInterruptedException(it);
            } catch (Exception e) {
                throw new RemoteException(
                        String.format("%s bind %s fail", serverConfig.getServerName(), serverConfig.getListenPort()),
                        e);
            }
        }
    }

    /**
     * init netty channel
     *
     * @param ch socket channel
     */
    private void initNettyChannel(SocketChannel ch) {
        ch.pipeline()
                .addLast("encoder", new TransporterEncoder())
                .addLast("decoder", new TransporterDecoder())
                .addLast("server-idle-handle",
                        new IdleStateHandler(serverConfig.getConnectionIdleTime(), 0, 0, TimeUnit.MILLISECONDS))
                .addLast("handler", channelHandler);
    }

    void registerMethodInvoker(ServerMethodInvoker methodInvoker) {
        channelHandler.registerMethodInvoker(methodInvoker);
    }

    void close() {
        if (isStarted.compareAndSet(true, false)) {
            log.info("{} closing", serverConfig.getServerName());
            try {
                if (serverBootstrapChannel != null) {
                    serverBootstrapChannel.close().sync();
                    log.info("{} stop bind at port: {}", serverConfig.getServerName(), serverConfig.getListenPort());
                }
                if (bossGroup != null) {
                    this.bossGroup.shutdownGracefully();
                }
                if (workGroup != null) {
                    this.workGroup.shutdownGracefully();
                }
                methodInvokerExecutor.shutdownNow();
            } catch (InterruptedException it) {
                ThreadUtils.consumeInterruptedException(it);
            } catch (Exception ex) {
                log.error("{} close failed", serverConfig.getServerName(), ex);
            }
            log.info("{} closed", serverConfig.getServerName());
        }
    }
}
