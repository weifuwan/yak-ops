package io.yak.ops.alert.service;

import io.yak.ops.alert.config.AlertConfig;
import io.yak.ops.registry.api.Registry;
import io.yak.ops.registry.api.enums.RegistryNodeType;
import io.yak.ops.registry.api.ha.AbstractHAServer;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AlertHAServer extends AbstractHAServer {

    public AlertHAServer(final Registry registry, final AlertConfig alertConfig) {
        super(registry, RegistryNodeType.ALERT_HA_LEADER.getRegistryPath(), alertConfig.getAlertServerAddress());
    }

    @Override
    public void start() {
        super.start();
        log.info("AlertHAServer started...");
    }

    @Override
    public void close() {
        log.info("AlertHAServer shutdown...");
    }
}
