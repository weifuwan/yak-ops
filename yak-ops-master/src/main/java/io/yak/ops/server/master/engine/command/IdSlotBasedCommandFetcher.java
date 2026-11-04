package io.yak.ops.server.master.engine.command;

import io.yak.ops.dao.entity.Command;
import io.yak.ops.dao.repository.CommandDao;
import io.yak.ops.server.master.cluster.MasterSlotManager;
import io.yak.ops.server.master.config.CommandFetchStrategy;
import io.yak.ops.server.master.metrics.WorkflowInstanceMetrics;

import java.util.Collections;
import java.util.List;

import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

/**
 * The command fetcher which is fetch commands by command id and slot.
 */
@Slf4j
public class IdSlotBasedCommandFetcher implements ICommandFetcher {

    private final CommandFetchStrategy.IdSlotBasedFetchConfig idSlotBasedFetchConfig;

    private final CommandDao commandDao;

    private final MasterSlotManager masterSlotManager;

    public IdSlotBasedCommandFetcher(CommandFetchStrategy.IdSlotBasedFetchConfig idSlotBasedFetchConfig,
                                     MasterSlotManager masterSlotManager,
                                     CommandDao commandDao) {
        this.idSlotBasedFetchConfig = idSlotBasedFetchConfig;
        this.masterSlotManager = masterSlotManager;
        this.commandDao = commandDao;
    }

    // We use transaction here to ensure that if mysql is configured at master/slave mode, this query will be routed to
    // the master db.
    @Override
    @Transactional
    public List<Command> fetchCommands() {
        long scheduleStartTime = System.currentTimeMillis();
        if (!masterSlotManager.checkSlotValid()) {
            log.warn("MasterSlotManager check slot ({} -> {})is invalidated.",
                    masterSlotManager.getCurrentMasterSlot(), masterSlotManager.getTotalMasterSlots());
            return Collections.emptyList();
        }
        int currentSlotIndex = masterSlotManager.getCurrentMasterSlot();
        int totalSlot = masterSlotManager.getTotalMasterSlots();
        List<Command> commands = commandDao.queryCommandByIdSlot(
                currentSlotIndex,
                totalSlot,
                idSlotBasedFetchConfig.getIdStep(),
                idSlotBasedFetchConfig.getFetchSize());
        long cost = System.currentTimeMillis() - scheduleStartTime;
        log.debug("[Slot-{}/{}] Fetch {} commands in {}ms.", currentSlotIndex, totalSlot, commands.size(), cost);
        WorkflowInstanceMetrics.recordCommandQueryTime(cost);
        return commands;
    }

}
