package io.yak.ops.server.master.cluster;

import io.yak.ops.common.enums.ServerStatus;

import java.util.List;
import java.util.Optional;

public interface IClusters<S extends IClusters.IServerMetadata> {

    List<S> getServers();

    Optional<S> getServer(final String address);

    void registerListener(IClustersChangeListener<S> listener);

    interface IServerMetadata {

        String getAddress();

        ServerStatus getServerStatus();

    }

    interface IClustersChangeListener<S extends IServerMetadata> {

        void onServerAdded(S server);

        void onServerRemove(S server);

        void onServerUpdate(S server);

    }

    interface ServerAddedListener<S extends IServerMetadata> extends IClustersChangeListener<S> {

        @Override
        default void onServerRemove(S server) {
            // only care about server added
        }

        @Override
        default void onServerUpdate(S server) {
            // only care about server added
        }

    }

    interface ServerRemovedListener<S extends IServerMetadata> extends IClustersChangeListener<S> {

        @Override
        default void onServerAdded(S server) {
            // only care about server removed
        }

        @Override
        void onServerRemove(S server);

        @Override
        default void onServerUpdate(S server) {
            // only care about server added
        }

    }

}
