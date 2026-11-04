package io.yak.ops.extract.base.client;

import io.yak.ops.extract.base.utils.Host;

import java.lang.reflect.Method;

abstract class AbstractClientMethodInvoker implements ClientMethodInvoker {

    protected final String methodIdentifier;

    protected final NettyRemotingClient nettyRemotingClient;

    protected final Method localMethod;

    protected final Host serverHost;

    AbstractClientMethodInvoker(Host serverHost, Method localMethod, NettyRemotingClient nettyRemotingClient) {
        this.serverHost = serverHost;
        this.localMethod = localMethod;
        this.nettyRemotingClient = nettyRemotingClient;
        this.methodIdentifier = localMethod.toGenericString();
    }

}
