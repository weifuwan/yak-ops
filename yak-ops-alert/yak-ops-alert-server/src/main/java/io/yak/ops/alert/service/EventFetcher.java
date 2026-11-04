package io.yak.ops.alert.service;

import java.util.List;

/**
 * The interface responsible for fetching events.
 *
 * @param <T> the type of event
 */
public interface EventFetcher<T> {

    void start();

    List<T> fetchPendingEvent(int eventOffset);

    void shutdown();
}
