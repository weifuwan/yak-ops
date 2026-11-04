package io.yak.ops.server.master.engine;

public interface IWorkflowSerialCoordinator extends AutoCloseable {

    void start();

    @Override
    void close();

}
