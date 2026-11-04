package io.yak.ops.registry.api.ha;

public interface ServerStatusChangeListener {

    void change(final HAServer.ServerStatus originStatus, final HAServer.ServerStatus currentStatus);

}
