package io.yak.ops.registry.api.ha;

/**
 * Interface for HA server, used to select a active server from multiple servers.
 * In HA mode, there are multiple servers, only one server is active, others are standby.
 */
public interface HAServer extends AutoCloseable {

    /**
     * Start the server.
     */
    void start();

    /**
     * Judge whether the server is active.
     *
     * @return true if the current server is active.
     */
    boolean isActive();

    /**
     * Participate in the election of active server, this method will block until the server is active.
     */
    boolean participateElection();

    /**
     * Add a listener to listen to the status change of the server.
     *
     * @param listener listener to add.
     */
    void addServerStatusChangeListener(ServerStatusChangeListener listener);

    /**
     * Get the status of the server.
     *
     * @return the status of the server.
     */
    ServerStatus getServerStatus();

    /**
     * Shutdown the server, release resources.
     */
    @Override
    void close();

    enum ServerStatus {
        ACTIVE,
        STAND_BY,
        ;
    }

}
