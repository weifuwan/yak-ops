package io.yak.ops.server.master.engine.workflow.serial;

public interface ISerialCommandHandler {

    void handle(SerialCommandsGroup serialCommandsGroup);

}
