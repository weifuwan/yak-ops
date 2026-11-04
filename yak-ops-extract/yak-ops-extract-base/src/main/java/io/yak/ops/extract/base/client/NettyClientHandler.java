package io.yak.ops.extract.base.client;

import io.yak.ops.extract.base.StandardRpcResponse;
import io.yak.ops.extract.base.future.ResponseFuture;
import io.yak.ops.extract.base.protocal.HeartBeatTransporter;
import io.yak.ops.extract.base.protocal.Transporter;
import io.yak.ops.extract.base.serialize.JsonSerializer;
import io.yak.ops.extract.base.utils.ChannelUtils;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

@ChannelHandler.Sharable
@Slf4j
public class NettyClientHandler extends ChannelInboundHandlerAdapter {

    private final NettyRemotingClient nettyRemotingClient;

    public NettyClientHandler(NettyRemotingClient nettyRemotingClient) {
        this.nettyRemotingClient = nettyRemotingClient;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.info("Channel inactive: {}", ctx.channel());
        nettyRemotingClient.onChannelInactive(ChannelUtils.toAddress(ctx.channel()));
        ctx.channel().close();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        processReceived((Transporter) msg);
    }

    private void processReceived(final Transporter transporter) {
        ResponseFuture future = ResponseFuture.getFuture(transporter.getHeader().getOpaque());
        if (future == null) {
            log.warn("Cannot find the ResponseFuture if transporter: {}", transporter);
            return;
        }
        StandardRpcResponse deserialize = JsonSerializer.deserialize(transporter.getBody(), StandardRpcResponse.class);
        future.setIRpcResponse(deserialize);
        future.putResponse(deserialize);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("NettyClientHandler catch an exception on channel: {}", ctx.channel(), cause);
        nettyRemotingClient.onChannelInactive(ChannelUtils.toAddress(ctx.channel()));
        ctx.channel().close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            ctx.channel()
                    .writeAndFlush(HeartBeatTransporter.getHeartBeatTransporter())
                    .addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
            if (log.isDebugEnabled()) {
                log.info("Client send heartbeat to: {}", ctx.channel().remoteAddress());
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

}
