package io.yak.ops.server.master.engine.command;

import io.yak.ops.dao.entity.Command;

import java.util.List;

/**
 * The command fetcher used to fetch commands
 */
public interface ICommandFetcher {

    /**
     * Fetch commands
     *
     * @return command list which need to be handled
     */
    List<Command> fetchCommands();

}
