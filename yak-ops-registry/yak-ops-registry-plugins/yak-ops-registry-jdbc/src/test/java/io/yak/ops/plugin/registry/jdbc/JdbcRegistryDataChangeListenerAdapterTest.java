package io.yak.ops.plugin.registry.jdbc;

import io.yak.ops.registry.api.Event;
import io.yak.ops.registry.api.SubscribeListener;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.google.common.truth.Truth;

class JdbcRegistryDataChangeListenerAdapterTest {

    private static final String WATCHED_PATH = "/nodes/master";

    /**
     * The REMOVE event must carry the deleted node value as eventData, otherwise downstream listeners
     * (e.g. AbstractClusterSubscribeListener / AbstractHAServer) cannot parse the removed server and will
     * silently drop the event, leaving stale registry data in memory.
     */
    @Test
    void onJdbcRegistryDataDeleted_shouldInjectDeletedValueAsEventData() {
        final String eventPath = "/nodes/master/127.0.0.1:5678";
        final String deletedValue = "{\"address\":\"127.0.0.1:5678\"}";
        final AtomicReference<Event> notified = new AtomicReference<>();

        final JdbcRegistryDataChangeListenerAdapter adapter = new JdbcRegistryDataChangeListenerAdapter(
                WATCHED_PATH, capturingListener(notified, SubscribeListener.SubscribeScope.CHILDREN_ONLY));

        adapter.onJdbcRegistryDataDeleted(eventPath, deletedValue);

        final Event event = notified.get();
        Truth.assertThat(event).isNotNull();
        Truth.assertThat(event.getType()).isEqualTo(Event.Type.REMOVE);
        Truth.assertThat(event.getWatchedPath()).isEqualTo(WATCHED_PATH);
        Truth.assertThat(event.getEventPath()).isEqualTo(eventPath);
        Truth.assertThat(event.getEventData()).isEqualTo(deletedValue);
    }

    private SubscribeListener capturingListener(final AtomicReference<Event> holder,
                                                final SubscribeListener.SubscribeScope scope) {
        return new SubscribeListener() {

            @Override
            public void notify(final Event event) {
                holder.set(event);
            }

            @Override
            public SubscribeScope getSubscribeScope() {
                return scope;
            }
        };
    }
}
