package io.yak.ops.extract.base.server;

import static com.google.common.base.Preconditions.checkNotNull;

import io.yak.ops.extract.base.StandardRpcRequest;
import io.yak.ops.extract.base.StandardRpcResponse;
import io.yak.ops.extract.base.protocal.HeartBeatTransporter;
import io.yak.ops.extract.base.protocal.Transporter;
import io.yak.ops.extract.base.protocal.TransporterHeader;
import io.yak.ops.extract.base.serialize.JsonSerializer;
import io.yak.ops.extract.base.utils.ChannelUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import com.google.common.collect.Lists;

import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChannelHandler.Sharable
class JdkDynamicServerHandler extends ChannelInboundHandlerAdapter {

    private final ExecutorService methodInvokeExecutor;

    private final Map<String, ServerMethodInvoker> methodInvokerMap;

    JdkDynamicServerHandler(ExecutorService methodInvokeExecutor) {
        this.methodInvokeExecutor = methodInvokeExecutor;
        this.methodInvokerMap = new ConcurrentHashMap<>();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        ctx.channel().close();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        processReceived(ctx.channel(), (Transporter) msg);
    }

    public void registerMethodInvoker(ServerMethodInvoker methodInvoker) {
        checkNotNull(methodInvoker);
        checkNotNull(methodInvoker.getMethodIdentify());

        methodInvokerMap.put(methodInvoker.getMethodIdentify(), methodInvoker);
    }

    private void processReceived(final Channel channel, final Transporter transporter) {
        final String methodIdentifier = transporter.getHeader().getMethodIdentifier();
        if (HeartBeatTransporter.METHOD_IDENTIFY.equals(methodIdentifier)) {
            if (log.isDebugEnabled()) {
                log.debug("server receive heart beat from: host: {}", ChannelUtils.getRemoteAddress(channel));
            }
            return;
        }
        ServerMethodInvoker methodInvoker = methodInvokerMap.get(methodIdentifier);
        try {
            if (methodInvoker == null) {
                log.error("Cannot find the ServerMethodInvoker of : {}", transporter);
                StandardRpcResponse iRpcResponse =
                        StandardRpcResponse.fail("Cannot find the ServerMethodInvoker of " + methodIdentifier);
                TransporterHeader transporterHeader =
                        TransporterHeader.of(transporter.getHeader().getOpaque(), methodIdentifier);
                Transporter response = Transporter.of(transporterHeader, iRpcResponse);
                channel.writeAndFlush(response);
                return;
            }
            methodInvokeExecutor.execute(() -> {
                StandardRpcResponse iRpcResponse;
                try {
                    StandardRpcRequest standardRpcRequest =
                            JsonSerializer.deserialize(transporter.getBody(), StandardRpcRequest.class);
                    Object[] args;
                    if (standardRpcRequest.getArgs() == null || standardRpcRequest.getArgs().length == 0) {
                        args = null;
                    } else {
                        if (!methodInvoker.isParameterTypeValidated(standardRpcRequest.getArgsTypes())) {
                            throw new IllegalArgumentException(
                                    "Parameter types: " + Lists.newArrayList(standardRpcRequest.getArgsTypes())
                                            + " do not match the method signature.");
                        }
                        args = new Object[standardRpcRequest.getArgs().length];
                        for (int i = 0; i < standardRpcRequest.getArgs().length; i++) {
                            args[i] = JsonSerializer.deserialize(standardRpcRequest.getArgs()[i],
                                    standardRpcRequest.getArgsTypes()[i]);
                        }
                    }
                    Object result = methodInvoker.invoke(args);
                    if (result == null) {
                        iRpcResponse = StandardRpcResponse.success(null, null);
                    } else {
                        iRpcResponse = StandardRpcResponse.success(JsonSerializer.serialize(result), result.getClass());
                    }
                } catch (Throwable e) {
                    log.error("Invoke method {} failed, {}.", methodIdentifier, e.getMessage(), e);
                    iRpcResponse = StandardRpcResponse.fail(e.getMessage());
                }
                TransporterHeader transporterHeader =
                        TransporterHeader.of(transporter.getHeader().getOpaque(), methodIdentifier);
                Transporter response = Transporter.of(transporterHeader, iRpcResponse);
                channel.writeAndFlush(response);
            });
        } catch (RejectedExecutionException e) {
            log.warn("NettyRemotingServer's thread pool is full, discard msg {} from {}", transporter,
                    ChannelUtils.getRemoteAddress(channel));
            StandardRpcResponse iRpcResponse = StandardRpcResponse.fail("NettyRemotingServer's thread pool is full");
            TransporterHeader transporterHeader =
                    TransporterHeader.of(transporter.getHeader().getOpaque(), methodIdentifier);
            Transporter response = Transporter.of(transporterHeader, iRpcResponse);
            channel.writeAndFlush(response);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("exceptionCaught : {}", cause.getMessage(), cause);
        ctx.channel().close();
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) {
        Channel ch = ctx.channel();
        ChannelConfig config = ch.config();

        if (!ch.isWritable()) {
            if (log.isWarnEnabled()) {
                log.warn("{} is not writable, over high water level : {}",
                        ch, config.getWriteBufferHighWaterMark());
            }

            config.setAutoRead(false);
        } else {
            if (log.isWarnEnabled()) {
                log.warn("{} is writable, to low water : {}", ch, config.getWriteBufferLowWaterMark());
            }
            config.setAutoRead(true);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                log.warn("Not receive heart beat from: {}, will close the channel", ctx.channel().remoteAddress());
                ctx.close();
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

}
