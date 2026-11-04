package io.yak.ops.plugin.registry.jdbc.server;

public interface IRegistryRowChangeNotifier<T> {

    void start();

    void subscribeRegistryRowChange(RegistryRowChangeListener<T> registryRowChangeListener);

    interface RegistryRowChangeListener<T> {

        void onRegistryRowUpdated(T data);

        void onRegistryRowAdded(T data);

        void onRegistryRowDeleted(T data);
    }

}
