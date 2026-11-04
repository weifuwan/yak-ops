package io.yak.ops.plugin.registry.jdbc;

import io.yak.ops.plugin.registry.jdbc.server.JdbcRegistryDataChangeListener;
import io.yak.ops.registry.api.Event;
import io.yak.ops.registry.api.SubscribeListener;

public class JdbcRegistryDataChangeListenerAdapter implements JdbcRegistryDataChangeListener {

    private final String watchedPath;
    private final SubscribeListener listener;

    public JdbcRegistryDataChangeListenerAdapter(final String watchedPath, final SubscribeListener listener) {
        this.watchedPath = watchedPath;
        this.listener = listener;
    }

    @Override
    public void onJdbcRegistryDataChanged(String eventPath, String value) {
        if (!isPathMatch(watchedPath, eventPath, listener.getSubscribeScope())) {
            return;
        }
        final Event event = Event.builder()
                .watchedPath(watchedPath)
                .eventPath(eventPath)
                .eventData(value)
                .type(Event.Type.UPDATE)
                .build();
        listener.notify(event);
    }

    @Override
    public void onJdbcRegistryDataDeleted(String eventPath, String value) {
        if (!isPathMatch(watchedPath, eventPath, listener.getSubscribeScope())) {
            return;
        }
        final Event event = Event.builder()
                .watchedPath(watchedPath)
                .eventPath(eventPath)
                .eventData(value)
                .type(Event.Type.REMOVE)
                .build();
        listener.notify(event);
    }

    @Override
    public void onJdbcRegistryDataAdded(String eventPath, String value) {
        if (!isPathMatch(watchedPath, eventPath, listener.getSubscribeScope())) {
            return;
        }
        final Event event = Event.builder()
                .watchedPath(watchedPath)
                .eventPath(eventPath)
                .eventData(value)
                .type(Event.Type.ADD)
                .build();
        listener.notify(event);
    }

    private boolean isPathMatch(final String subscribePath,
                                final String eventPath,
                                final SubscribeListener.SubscribeScope subscribeScope) {
        switch (subscribeScope) {
            case PATH_ONLY:
                return KeyUtils.isSamePath(subscribePath, eventPath);
            case CHILDREN_ONLY:
                return KeyUtils.isParent(subscribePath, eventPath);
            case ALL:
                return KeyUtils.isParent(subscribePath, eventPath)
                        || KeyUtils.isSamePath(subscribePath, eventPath);
            default:
                throw new IllegalArgumentException("Invalid subscribe scope " + subscribeScope);
        }
    }

}
