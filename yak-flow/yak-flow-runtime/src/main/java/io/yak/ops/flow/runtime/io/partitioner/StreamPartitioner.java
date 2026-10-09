package io.yak.ops.flow.runtime.io.partitioner;

/**
 * Select one downstream subpartition for a record. Every upstream StreamTask owns its partitioner
 * instance, so stateful strategies such as REBALANCE never share a counter between producers.
 */
@FunctionalInterface
public interface StreamPartitioner<T> {

    int selectChannel(T record, int numberOfChannels) throws Exception;
}
