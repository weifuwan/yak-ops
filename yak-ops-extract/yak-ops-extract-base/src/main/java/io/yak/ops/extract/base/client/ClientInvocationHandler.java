package io.yak.ops.extract.base.client;

import static com.google.common.base.Preconditions.checkNotNull;

import io.yak.ops.extract.base.RpcMethod;
import io.yak.ops.extract.base.utils.Host;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class ClientInvocationHandler implements InvocationHandler {

    private final NettyRemotingClient nettyRemotingClient;

    private final Map<String, ClientMethodInvoker> methodInvokerMap;

    private final Host serverHost;

    ClientInvocationHandler(Host serverHost, NettyRemotingClient nettyRemotingClient) {
        this.serverHost = checkNotNull(serverHost);
        this.nettyRemotingClient = checkNotNull(nettyRemotingClient);
        this.methodInvokerMap = new ConcurrentHashMap<>();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getAnnotation(RpcMethod.class) == null) {
            return method.invoke(proxy, args);
        }
        ClientMethodInvoker methodInvoker = methodInvokerMap.computeIfAbsent(
                method.toGenericString(), m -> new SyncClientMethodInvoker(serverHost, method, nettyRemotingClient));
        try {
            return methodInvoker.invoke(proxy, method, args);
        } catch (UndeclaredThrowableException undeclaredThrowableException) {
            throw undeclaredThrowableException.getCause();
        } catch (Throwable throwable) {
            throw throwable;
        }
    }

}
