package io.yak.ops.extract.base.client;

interface IRpcClientProxyFactory {

    /**
     * Create the client proxy.
     *
     * @param serverHost      which server host to connect
     * @param clientInterface which need to be proxied
     * @param <T>             InterfaceType
     * @return proxy client
     */
    <T> T getProxyClient(String serverHost, Class<T> clientInterface);

}
