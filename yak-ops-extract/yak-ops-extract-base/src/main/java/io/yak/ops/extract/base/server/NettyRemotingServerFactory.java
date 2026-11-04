package io.yak.ops.extract.base.server;

import io.yak.ops.extract.base.config.NettyServerConfig;

import lombok.experimental.UtilityClass;

@UtilityClass
class NettyRemotingServerFactory {

    NettyRemotingServer buildNettyRemotingServer(NettyServerConfig nettyServerConfig) {
        return new NettyRemotingServer(nettyServerConfig);
    }
}
