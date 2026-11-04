package io.yak.ops.server.worker.config;

import io.yak.ops.meter.metrics.BaseServerLoadProtectionConfig;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WorkerServerLoadProtectionConfig extends BaseServerLoadProtectionConfig {
}
