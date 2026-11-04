package io.yak.ops.registry.api.ha;

import static com.google.common.base.Preconditions.checkNotNull;

import io.yak.ops.common.thread.ThreadUtils;
import io.yak.ops.registry.api.Event;
import io.yak.ops.registry.api.Registry;
import io.yak.ops.registry.api.SubscribeListener;

import java.util.List;

import com.google.common.collect.Lists;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractHAServer implements HAServer {

    private final Registry registry;

    private final String selectorPath;

    private final String serverIdentify;

    private ServerStatus serverStatus;

    private final List<ServerStatusChangeListener> serverStatusChangeListeners;

    private static final long DEFAULT_RETRY_INTERVAL = 5_000;

    private static final int DEFAULT_MAX_RETRY_TIMES = 20;

    public AbstractHAServer(final Registry registry, final String selectorPath, final String serverIdentify) {
        this.registry = registry;
        this.selectorPath = checkNotNull(selectorPath);
        this.serverIdentify = checkNotNull(serverIdentify);
        this.serverStatus = ServerStatus.STAND_BY;
        this.serverStatusChangeListeners = Lists.newArrayList(new DefaultServerStatusChangeListener());
    }

    @Override
    public void start() {
        registry.subscribe(selectorPath, new SubscribeListener() {

            @Override
            public void notify(Event event) {
                if (Event.Type.REMOVE.equals(event.getType())) {
                    if (serverIdentify.equals(event.getEventData())) {
                        statusChange(ServerStatus.STAND_BY);
                    } else {
                        if (participateElection()) {
                            statusChange(ServerStatus.ACTIVE);
                        }
                    }
                }
            }

            @Override
            public SubscribeScope getSubscribeScope() {
                return SubscribeScope.PATH_ONLY;
            }
        });

        if (participateElection()) {
            statusChange(ServerStatus.ACTIVE);
        } else {
            log.info("Server {} is standby", serverIdentify);
        }
    }

    @Override
    public boolean isActive() {
        return ServerStatus.ACTIVE.equals(getServerStatus());
    }

    @Override
    public boolean participateElection() {
        final String electionLock = selectorPath + "-lock";
        // If meet exception during participate election, will retry.
        // This can avoid the situation that the server is not elected as leader due to network jitter.
        for (int i = 0; i < DEFAULT_MAX_RETRY_TIMES; i++) {
            try {
                try {
                    if (registry.acquireLock(electionLock)) {
                        if (!registry.exists(selectorPath)) {
                            registry.put(selectorPath, serverIdentify, true);
                            return true;
                        }
                        return serverIdentify.equals(registry.get(selectorPath));
                    }
                    return false;
                } finally {
                    registry.releaseLock(electionLock);
                }
            } catch (Exception e) {
                log.error("Participate election error, meet an exception, will retry after {}ms",
                        DEFAULT_RETRY_INTERVAL, e);
                ThreadUtils.sleep(DEFAULT_RETRY_INTERVAL);
            }
        }
        throw new IllegalStateException(
                "Participate election failed after retry " + DEFAULT_MAX_RETRY_TIMES + " times");
    }

    @Override
    public void addServerStatusChangeListener(ServerStatusChangeListener listener) {
        serverStatusChangeListeners.add(listener);
    }

    @Override
    public ServerStatus getServerStatus() {
        return serverStatus;
    }

    private void statusChange(ServerStatus targetStatus) {
        final ServerStatus originStatus = serverStatus;
        serverStatus = targetStatus;
        synchronized (this) {
            try {
                serverStatusChangeListeners.forEach(listener -> listener.change(originStatus, serverStatus));
            } catch (Exception ex) {
                log.error("Trigger ServerStatusChangeListener from {} -> {} error", originStatus, targetStatus, ex);
            }
        }
    }
}
