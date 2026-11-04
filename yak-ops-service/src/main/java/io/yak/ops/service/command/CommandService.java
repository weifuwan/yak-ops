package io.yak.ops.service.command;

import io.yak.ops.dao.entity.Command;

public interface CommandService {

    /**
     * Save error command, and delete original command. If the given command has already been moved into error command,
     * will throw {@link java.sql.SQLIntegrityConstraintViolationException ).
     * @param command command
     * @param message message
     */
    void moveToErrorCommand(Command command, String message);

    /**
     * Create new command
     * @param command command
     * @return result
     */
    int createCommand(Command command);

    /**
     * check the input command exists in queue list
     *
     * @param command command
     * @return create command result
     */
    boolean verifyIsNeedCreateCommand(Command command);

}
