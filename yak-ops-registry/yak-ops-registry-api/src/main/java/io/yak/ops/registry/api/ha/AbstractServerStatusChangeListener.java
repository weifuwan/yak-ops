package io.yak.ops.registry.api.ha;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractServerStatusChangeListener implements ServerStatusChangeListener {

    @Override
    public void change(HAServer.ServerStatus originStatus, HAServer.ServerStatus currentStatus) {
        if (originStatus == HAServer.ServerStatus.ACTIVE) {
            if (currentStatus == HAServer.ServerStatus.STAND_BY) {
                changeToStandBy();
            }
        } else if (originStatus == HAServer.ServerStatus.STAND_BY) {
            if (currentStatus == HAServer.ServerStatus.ACTIVE) {
                changeToActive();
            }
        }
    }

    public abstract void changeToActive();

    public abstract void changeToStandBy();
}
