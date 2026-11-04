package io.yak.ops.alert.service;

/**
 * The interface responsible for consuming event from upstream, e.g {@link EventPendingQueue}.
 *
 * @param <T> the type of event
 */
public interface EventLoop<T> {

    /**
     * Start the event loop, once the event loop is started, it will keep consuming event from upstream.
     */
    void start();

    /**
     * Handle the given event.
     */
    void handleEvent(T event);

    /**
     * Get the count of handling event.
     */
    int getHandlingEventCount();

    /**
     * Shutdown the event loop, once the event loop is shutdown, it will stop consuming event from upstream.
     */
    void shutdown();

}
