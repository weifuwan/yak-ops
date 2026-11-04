package io.yak.ops.extract.base.client;

import io.yak.ops.extract.base.utils.Host;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * This class is used to create a proxy client which will transform local method invocation to remove invocation.
 */
@Slf4j
class JdkDynamicRpcClientProxyFactory implements IRpcClientProxyFactory {

    private final NettyRemotingClient nettyRemotingClient;

    private static final LoadingCache<String, Map<String, Object>> proxyClientCache = CacheBuilder.newBuilder()
            // expire here to remove dead host which is never used
            // It's safe to remove dead host here because the client will be recreated when needed
            // and the client is only a proxy client, it will not hold any resource
            .expireAfterAccess(Duration.ofHours(1))
            .removalListener((RemovalListener<String, Map<String, Object>>) notification -> {
                log.warn("Remove DynamicRpcClientProxy cache for host: {}", notification.getKey());
                notification.getValue().clear();
            })
            .build(new CacheLoader<String, Map<String, Object>>() {

                @Override
                public Map<String, Object> load(String host) {
                    log.info("Create DynamicRpcClientProxy cache for host: {}", host);
                    return new ConcurrentHashMap<>();
                }
            });

    JdkDynamicRpcClientProxyFactory(NettyRemotingClient nettyRemotingClient) {
        this.nettyRemotingClient = nettyRemotingClient;
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    @Override
    public <T> T getProxyClient(String serverHost, Class<T> clientInterface) {
        return (T) proxyClientCache.get(serverHost)
                .computeIfAbsent(clientInterface.getName(), key -> newProxyClient(serverHost, clientInterface));
    }

    @SuppressWarnings("unchecked")
    private <T> T newProxyClient(String serverHost, Class<T> clientInterface) {
        return (T) Proxy.newProxyInstance(
                clientInterface.getClassLoader(),
                new Class[]{clientInterface},
                new ClientInvocationHandler(Host.of(serverHost), nettyRemotingClient));
    }
}
