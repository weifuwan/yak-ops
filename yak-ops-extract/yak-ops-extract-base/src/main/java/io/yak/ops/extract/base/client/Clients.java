package io.yak.ops.extract.base.client;

import io.yak.ops.extract.base.config.NettyClientConfig;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The factory class for creating a dynamic proxy client.
 * <pre>
 *     final IService proxyClient = Clients
 *            .withService(IService.class)
 *            .withHost(serverAddress);
 * </pre>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Clients {

    private static final JdkDynamicRpcClientProxyFactory jdkDynamicRpcClientProxyFactory =
            new JdkDynamicRpcClientProxyFactory(
                    NettyRemotingClientFactory.buildNettyRemotingClient(
                            new NettyClientConfig()));

    public static <T> JdkDynamicRpcClientProxyBuilder<T> withService(Class<T> serviceClazz) {
        return new JdkDynamicRpcClientProxyBuilder<>(serviceClazz);
    }

    public static class JdkDynamicRpcClientProxyBuilder<T> {

        private final Class<T> serviceClazz;

        public JdkDynamicRpcClientProxyBuilder(Class<T> serviceClazz) {
            this.serviceClazz = serviceClazz;
        }

        public T withHost(String serviceHost) {
            return jdkDynamicRpcClientProxyFactory.getProxyClient(serviceHost, serviceClazz);
        }
    }

}
