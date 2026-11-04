package io.yak.ops.server.master.cluster;

import io.yak.ops.server.master.config.MasterConfig;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MasterSlotManager implements IMasterSlotReBalancer {

    private final MasterConfig masterConfig;

    private volatile int currentSlot = -1;

    private volatile int totalSlots = 0;

    public MasterSlotManager(final MasterConfig masterConfig) {
        this.masterConfig = masterConfig;
    }

    /**
     * Get the current master slot, if the slot is -1, it means the master slot is not available.
     */
    public int getCurrentMasterSlot() {
        return currentSlot;
    }

    /**
     * Get the total master slots.
     */
    public int getTotalMasterSlots() {
        return totalSlots;
    }

    public boolean checkSlotValid() {
        return totalSlots > 0 && currentSlot >= 0;
    }

    @Override
    public void doReBalance(List<MasterServerMetadata> normalMasterServers) {

        normalMasterServers =
                normalMasterServers.stream().sorted(MasterServerMetadata::compareTo).collect(Collectors.toList());

        int tmpCurrentSlot = -1;
        for (int i = 0; i < normalMasterServers.size(); i++) {
            if (normalMasterServers.get(i).getAddress().equals(masterConfig.getMasterAddress())) {
                tmpCurrentSlot = i;
                break;
            }
        }
        if (tmpCurrentSlot == -1) {
            log.warn(
                    "Do rebalance failed, cannot found the current master: {} in the normal master clusters: {}. Please check the current master server status",
                    masterConfig.getMasterAddress(), normalMasterServers);
            currentSlot = -1;
            return;
        }

        if (totalSlots == normalMasterServers.size() && currentSlot == tmpCurrentSlot) {
            log.debug("No need to rebalance, the currentSlot: {}, totalSlots: {} doesn't changed", currentSlot,
                    totalSlots);
            return;
        }
        totalSlots = normalMasterServers.size();
        currentSlot = tmpCurrentSlot;
        log.info("Do rebalance success, current master slot: {}, total master slots: {}", currentSlot, totalSlots);
    }
}
