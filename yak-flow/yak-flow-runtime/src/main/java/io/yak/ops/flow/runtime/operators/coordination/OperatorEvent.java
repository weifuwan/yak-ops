package io.yak.ops.flow.runtime.operators.coordination;

/**
 * Marker for local operator-coordination control messages.
 *
 * <p>Events are delivered as Java objects within the embedded JVM. Remote delivery would
 * require an explicit serialization and acknowledgment protocol. These messages are not
 * data records or checkpoint barriers.
 *
 * @author weifuwan
 */
public interface OperatorEvent {}
