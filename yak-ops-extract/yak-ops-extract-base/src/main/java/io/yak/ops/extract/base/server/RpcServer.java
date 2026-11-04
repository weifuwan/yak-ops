package io.yak.ops.extract.base.server;

import io.yak.ops.extract.base.RpcMethod;
import io.yak.ops.extract.base.RpcService;
import io.yak.ops.extract.base.config.NettyServerConfig;

import java.lang.reflect.Method;

import lombok.extern.slf4j.Slf4j;

/**
 * The RpcServer based on Netty. The server will register the method invoker and provide the service to the client.
 * Once the server is started, it will listen on the port and wait for the client to connect.
 * <pre>
 *          RpcServer rpcServer = new RpcServer(new NettyServerConfig());
 *          rpcServer.registerServerMethodInvokerProvider(new ServerMethodInvokerProviderImpl());
 *          rpcServer.start();
 * </pre>
 */
@Slf4j
public class RpcServer implements ServerMethodInvokerRegistry, AutoCloseable {

    private final NettyRemotingServer nettyRemotingServer;

    public RpcServer(NettyServerConfig nettyServerConfig) {
        this.nettyRemotingServer = NettyRemotingServerFactory.buildNettyRemotingServer(nettyServerConfig);
    }

    public void start() {
        nettyRemotingServer.start();
    }

    @Override
    public void registerServerMethodInvokerProvider(Object serverMethodInvokerProviderBean) {
        for (Class<?> anInterface : serverMethodInvokerProviderBean.getClass().getInterfaces()) {
            if (anInterface.getAnnotation(RpcService.class) == null) {
                continue;
            }
            for (Method method : anInterface.getDeclaredMethods()) {
                RpcMethod rpcMethod = method.getAnnotation(RpcMethod.class);
                if (rpcMethod == null) {
                    continue;
                }
                ServerMethodInvoker serverMethodInvoker =
                        new ServerMethodInvokerImpl(serverMethodInvokerProviderBean, method);
                nettyRemotingServer.registerMethodInvoker(serverMethodInvoker);
                log.debug("Register ServerMethodInvoker: {} to bean: {}",
                        serverMethodInvoker.getMethodIdentify(), serverMethodInvoker.getMethodProviderIdentify());
            }
        }
    }

    @Override
    public void close() {
        nettyRemotingServer.close();
    }
}
