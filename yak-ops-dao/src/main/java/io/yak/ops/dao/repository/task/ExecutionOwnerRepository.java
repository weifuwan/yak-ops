package io.yak.ops.dao.repository.task;

/** Records the process holding the durable embedded Task execution owner identity. */
public interface ExecutionOwnerRepository {

    /**
     * Verifies the registered host and state directory while locking the owner row in a transaction.
     *
     * @param hostId current host identity
     * @param stateRoot canonical local state directory protected by the process lock
     * @param incarnation identity of the current owning process
     */
    void register(String hostId, String stateRoot, String incarnation);
}
