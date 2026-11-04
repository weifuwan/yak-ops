package io.yak.ops.registry.api.ha;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DefaultServerStatusChangeListener extends AbstractServerStatusChangeListener {

    @Override
    public void changeToActive() {
        log.info("The status is active now.");
    }

    @Override
    public void changeToStandBy() {
        log.info("The status is standby now.");
    }
}
