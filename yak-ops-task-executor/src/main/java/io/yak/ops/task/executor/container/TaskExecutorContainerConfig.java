package io.yak.ops.task.executor.container;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskExecutorContainerConfig {

    private String containerName;

    @Builder.Default
    private int taskExecutorThreadPoolSize = Runtime.getRuntime().availableProcessors() * 2 + 1;

}
