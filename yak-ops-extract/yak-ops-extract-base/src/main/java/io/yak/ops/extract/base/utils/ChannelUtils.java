package io.yak.ops.extract.base.utils;

import io.yak.ops.common.utils.NetUtils;

import java.net.InetSocketAddress;

import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ChannelUtils {

    private ChannelUtils() {
        throw new IllegalStateException(ChannelUtils.class.getName());
    }

    /**
     * get local address
     *
     * @param channel channel
     * @return local address
     */
    public static String getLocalAddress(Channel channel) {
        return NetUtils.getHost(((InetSocketAddress) channel.localAddress()).getAddress());
    }

    /**
     * get remote address
     *
     * @param channel channel
     * @return remote address
     */
    public static String getRemoteAddress(Channel channel) {
        return toAddress(channel).getAddress();
    }

    /**
     * channel to address
     *
     * @param channel channel
     * @return address
     */
    public static Host toAddress(Channel channel) {
        InetSocketAddress socketAddress = ((InetSocketAddress) channel.remoteAddress());
        if (socketAddress == null) {
            // the remote channel already closed
            log.warn("The channel is already closed");
            return Host.EMPTY;
        }
        return new Host(NetUtils.getHost(socketAddress.getAddress()), socketAddress.getPort());
    }

}
