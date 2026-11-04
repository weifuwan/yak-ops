package io.yak.ops.server.master.config;

import io.yak.ops.meter.metrics.BaseServerLoadProtectionConfig;

import java.time.Duration;

import org.springframework.validation.Errors;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MasterServerLoadProtectionConfig extends BaseServerLoadProtectionConfig {

    private int maxConcurrentWorkflowInstances = Integer.MAX_VALUE;

    private Duration maxWorkflowInstanceRuntime = Duration.ofDays(0);

    private Duration maxTaskInstanceRuntime = Duration.ofDays(0);

    public void validate(Errors errors) {
        if (maxConcurrentWorkflowInstances <= 0) {
            errors.rejectValue("maxConcurrentWorkflowInstances", null,
                    "maxConcurrentWorkflowInstances must be greater than 0");
        }
        if (!maxWorkflowInstanceRuntime.isZero() &&
                maxWorkflowInstanceRuntime.compareTo(Duration.ofMinutes(1)) < 0) {
            errors.rejectValue("maxWorkflowInstanceRuntime", null,
                    "maxWorkflowInstanceRuntime must be 0 (disabled) or >= 1m");
        }
        if (!maxTaskInstanceRuntime.isZero() &&
                maxTaskInstanceRuntime.compareTo(Duration.ofMinutes(1)) < 0) {
            errors.rejectValue("maxTaskInstanceRuntime", null, "maxTaskInstanceRuntime must be 0 (disabled) or >= 1m");
        }
    }
}
