package io.yak.ops.dao.repository;

import io.yak.ops.dao.entity.Command;

import java.util.List;

public interface CommandDao extends IDao<Command> {

    /**
     * Query command by command id and server slot, return the command which match (commandId / step) %s totalSlot = currentSlotIndex
     *
     * @param currentSlotIndex current slot index
     * @param totalSlot        total slot number
     * @param idStep           id step in db
     * @param fetchNum         fetch number
     * @return command list
     */
    List<Command> queryCommandByIdSlot(int currentSlotIndex,
                                       int totalSlot,
                                       int idStep,
                                       int fetchNum);
}
