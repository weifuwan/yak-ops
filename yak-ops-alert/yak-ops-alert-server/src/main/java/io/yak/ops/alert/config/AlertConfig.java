package io.yak.ops.alert.config;

import io.yak.ops.common.utils.NetUtils;

import org.apache.commons.lang3.StringUtils;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@Component
@ConfigurationProperties("alert")
public final class AlertConfig implements Validator {

    private int port;

    private int waitTimeout;

    private Duration maxHeartbeatInterval = Duration.ofSeconds(60);

    private int senderParallelism = 100;

    private String alertServerAddress;

    @Override
    public boolean supports(Class<?> clazz) {
        return AlertConfig.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        AlertConfig alertConfig = (AlertConfig) target;

        if (maxHeartbeatInterval.getSeconds() <= 0) {
            errors.rejectValue("max-heartbeat-interval", null, "should be a valid duration");
        }

        if (senderParallelism <= 0) {
            errors.rejectValue("sender-parallelism", null, "should be a positive number");
        }

        if (StringUtils.isEmpty(alertServerAddress)) {
            alertConfig.setAlertServerAddress(NetUtils.getAddr(alertConfig.getPort()));
        }

        printConfig();
    }

    private void printConfig() {
        log.info("Alert config: port -> {}", port);
        log.info("Alert config: alertServerAddress -> {}", alertServerAddress);
        log.info("Alert config: maxHeartbeatInterval -> {}", maxHeartbeatInterval);
    }
}
