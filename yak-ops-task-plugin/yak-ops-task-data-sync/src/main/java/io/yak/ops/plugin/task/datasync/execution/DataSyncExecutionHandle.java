package io.yak.ops.plugin.task.datasync.execution;

import io.yak.ops.plugin.task.datasync.DataSyncTableMapping;

import java.util.Collections;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Stable execution metadata shared by execution, metrics and reporting services. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSyncExecutionHandle {

    private String executionId;

    private int taskInstanceId;

    private String jobId;

    private String deploymentMode;

    private String clusterId;

    private String restEndpoint;

    private long submittedAt;

    @Builder.Default
    private List<DataSyncTableMapping> tableMappings = Collections.emptyList();
}
