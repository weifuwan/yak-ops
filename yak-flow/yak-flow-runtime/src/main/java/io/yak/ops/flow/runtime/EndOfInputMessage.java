package io.yak.ops.flow.runtime;

/**
 * 有界 Source 已经不会再产生数据的 Channel 终止信号。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
enum EndOfInputMessage implements ChannelMessage {
    INSTANCE
}
