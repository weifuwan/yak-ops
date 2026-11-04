package io.yak.ops.server.worker.config;

import io.yak.ops.common.utils.NetUtils;
import io.yak.ops.registry.api.enums.RegistryNodeType;

import org.apache.commons.lang3.StringUtils;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.validation.annotation.Validated;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "worker")
@Slf4j
public class WorkerConfig implements Validator {

    private int listenPort = 1234;
    private Duration maxHeartbeatInterval = Duration.ofSeconds(10);
    private int hostWeight = 100;
    private WorkerServerLoadProtectionConfig serverLoadProtection = new WorkerServerLoadProtectionConfig();
    private String group;

    /**
     * This field doesn't need to set at config file, it will be calculated by workerIp:listenPort
     */
    private String workerAddress;
    private String workerRegistryPath;

    private TenantConfig tenantConfig = new TenantConfig();

    private PhysicalTaskConfig physicalTaskConfig = new PhysicalTaskConfig();

    @Override
    public boolean supports(Class<?> clazz) {
        return WorkerConfig.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        WorkerConfig workerConfig = (WorkerConfig) target;
        if (workerConfig.getMaxHeartbeatInterval().getSeconds() <= 0) {
            errors.rejectValue("max-heartbeat-interval", null, "should be a valid duration");
        }
        if (StringUtils.isEmpty(workerConfig.getWorkerAddress())) {
            workerConfig.setWorkerAddress(NetUtils.getAddr(workerConfig.getListenPort()));
        }

        workerConfig.setWorkerRegistryPath(
                RegistryNodeType.WORKER.getRegistryPath() + "/" + workerConfig.getWorkerAddress());

        if (StringUtils.isEmpty(group)) {
            workerConfig.setGroup("default");
        }

        printConfig();
    }

    private void printConfig() {
        String config =
                "\n****************************Worker Configuration**************************************" +
                        "\n  listen-port -> " + listenPort +
                        "\n  max-heartbeat-interval -> " + maxHeartbeatInterval +
                        "\n  host-weight -> " + hostWeight +
                        "\n  tenantConfig -> " + tenantConfig +
                        "\n  server-load-protection -> " + serverLoadProtection +
                        "\n  address -> " + workerAddress +
                        "\n  registry-path: " + workerRegistryPath +
                        "\n  physical-task-config -> " + physicalTaskConfig +
                        "\n  group -> " + group +
                        "\n****************************Worker Configuration**************************************";
        log.info(config);
    }
}
