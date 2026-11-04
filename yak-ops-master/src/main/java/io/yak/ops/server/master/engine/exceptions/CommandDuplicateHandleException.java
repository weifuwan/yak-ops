package io.yak.ops.server.master.engine.exceptions;

import io.yak.ops.dao.entity.Command;

public class CommandDuplicateHandleException extends RuntimeException {

    public CommandDuplicateHandleException(Command command) {
        super("The command: " + command.getId() + " has already been handled");
    }

}
