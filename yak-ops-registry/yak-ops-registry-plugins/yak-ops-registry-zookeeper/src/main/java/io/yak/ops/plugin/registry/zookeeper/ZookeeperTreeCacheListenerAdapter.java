package io.yak.ops.plugin.registry.zookeeper;

import io.yak.ops.registry.api.Event;
import io.yak.ops.registry.api.RegistryException;
import io.yak.ops.registry.api.SubscribeListener;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.TreeCacheEvent;
import org.apache.curator.framework.recipes.cache.TreeCacheListener;

import java.util.EnumSet;

public class ZookeeperTreeCacheListenerAdapter implements TreeCacheListener {

    private final String watchedPath;

    private final SubscribeListener listener;

    private static final EnumSet<TreeCacheEvent.Type> NODE_CHANGE_EVENTS =
            EnumSet.of(TreeCacheEvent.Type.NODE_REMOVED,
                    TreeCacheEvent.Type.NODE_UPDATED,
                    TreeCacheEvent.Type.NODE_ADDED);

    public ZookeeperTreeCacheListenerAdapter(final String watchedPath, final SubscribeListener listener) {
        this.listener = listener;
        this.watchedPath = watchedPath;
    }

    @Override
    public void childEvent(final CuratorFramework curatorFramework, final TreeCacheEvent event) {
        // When the event type is INITIALIZED or CONNECTION_SUSPENDED or CONNECTION_LOST or CONNECTION_RECONNECTED, the
        // data in the event is null by default
        if (!isNodeChangeEvent(event)) {
            return;
        }
        final String eventPath = event.getData().getPath();
        switch (listener.getSubscribeScope()) {
            case PATH_ONLY:
                if (eventPath.equals(watchedPath)) {
                    listener.notify(convertToEvent(event, watchedPath));
                }
                break;
            case CHILDREN_ONLY:
                if (!eventPath.equals(watchedPath)) {
                    listener.notify(convertToEvent(event, watchedPath));
                }
                break;
            case ALL:
                listener.notify(convertToEvent(event, watchedPath));
                break;
            default:
                throw new RegistryException("Unknown event scope: " + listener.getSubscribeScope());
        }
    }

    private boolean isNodeChangeEvent(TreeCacheEvent event) {
        return NODE_CHANGE_EVENTS.contains(event.getType());
    }

    private Event convertToEvent(TreeCacheEvent event, String watchedPath) {

        Event.Type type;
        switch (event.getType()) {
            case NODE_ADDED:
                type = Event.Type.ADD;
                break;
            case NODE_UPDATED:
                type = Event.Type.UPDATE;
                break;
            case NODE_REMOVED:
                type = Event.Type.REMOVE;
                break;
            default:
                throw new IllegalArgumentException("Unsupported event type: " + event.getType());
        }

        final ChildData data = event.getData();
        return Event.builder()
                .type(type)
                .watchedPath(watchedPath)
                .eventPath(data.getPath())
                .eventData(new String(data.getData()))
                .build();
    }
}
