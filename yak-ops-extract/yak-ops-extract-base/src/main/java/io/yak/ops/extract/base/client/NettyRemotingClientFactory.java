package io.yak.ops.extract.base.client;

import io.yak.ops.extract.base.config.NettyClientConfig;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class NettyRemotingClientFactory {

    public NettyRemotingClient buildNettyRemotingClient(NettyClientConfig nettyClientConfig) {
        return new NettyRemotingClient(nettyClientConfig);
    }
}
