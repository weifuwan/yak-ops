package io.yak.ops.server.master.engine.system.event;

public interface ISystemEventHandler<T extends AbstractSystemEvent> {

    void handle(final T systemEvent);

    SystemEventType matchState();
}
