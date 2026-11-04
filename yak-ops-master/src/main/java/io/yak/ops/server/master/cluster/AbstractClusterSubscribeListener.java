package io.yak.ops.server.master.cluster;

import io.yak.ops.registry.api.Event;
import io.yak.ops.registry.api.SubscribeListener;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractClusterSubscribeListener<T extends BaseServerMetadata> implements SubscribeListener {

    @Override
    public void notify(Event event) {
        try {
            // make sure the event is processed in order
            synchronized (this) {
                Event.Type type = event.getType();
                T server = parseServerFromHeartbeat(event.getEventData());
                if (server == null) {
                    log.error("Unknown cluster change event: {}", event);
                    return;
                }
                switch (type) {
                    case ADD:
                        log.info("Server {} added", server);
                        onServerAdded(server);
                        break;
                    case REMOVE:
                        log.warn("Server {} removed", server);
                        onServerRemove(server);
                        break;
                    case UPDATE:
                        log.debug("Server {} updated", server);
                        onServerUpdate(server);
                        break;
                    default:
                        break;
                }
            }
        } catch (Exception ex) {
            log.error("Notify cluster change event: {} failed", event, ex);
        }
    }

    @Override
    public SubscribeScope getSubscribeScope() {
        return SubscribeScope.CHILDREN_ONLY;
    }

    abstract T parseServerFromHeartbeat(String serverHeartBeatJson);

    public abstract void onServerAdded(T serverHeartBeat);

    public abstract void onServerRemove(T serverHeartBeat);

    public abstract void onServerUpdate(T serverHeartBeat);

}
