package io.yak.ops.server.master.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LogicTaskConfig {

    @Builder.Default
    private int taskExecutorThreadCount = Runtime.getRuntime().availableProcessors() * 2 + 1;
}
