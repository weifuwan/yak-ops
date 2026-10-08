package io.yak.ops.flow.runtime;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Local Execution Engine Source 与 Sink 之间的有界内存 Channel，通过阻塞队列提供最小背压。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
final class RowChannel {

    private final BlockingQueue<ChannelMessage> queue;

    RowChannel(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be greater than 0");
        }
        queue = new ArrayBlockingQueue<>(capacity);
    }

    void put(ChannelMessage message) throws InterruptedException {
        queue.put(Objects.requireNonNull(message, "message must not be null"));
    }

    ChannelMessage take() throws InterruptedException {
        return queue.take();
    }
}
