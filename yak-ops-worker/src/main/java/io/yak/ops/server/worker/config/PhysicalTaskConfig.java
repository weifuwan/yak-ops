package io.yak.ops.server.worker.config;

import lombok.Data;

@Data
public class PhysicalTaskConfig {

    private int taskExecutorThreadSize = Runtime.getRuntime().availableProcessors() * 2 + 1;

}
