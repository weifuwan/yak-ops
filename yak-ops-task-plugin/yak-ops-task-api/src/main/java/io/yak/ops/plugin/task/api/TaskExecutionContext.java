package io.yak.ops.plugin.task.api;

import io.yak.ops.plugin.task.api.enums.TaskTimeoutStrategy;
import io.yak.ops.plugin.task.api.model.Property;
import io.yak.ops.plugin.task.api.parameters.resource.ResourceParametersHelper;
import io.yak.ops.plugin.task.api.resource.ResourceContext;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * to master/worker task transport
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskExecutionContext implements Serializable {

    private static final long serialVersionUID = -1L;

    private int taskInstanceId;

    private String taskName;

    private long firstSubmitTime;

    private long startTime;

    private String taskType;

    private String workflowInstanceHost;

    private String host;

    private String executePath;

    private String logPath;

    private String appInfoPath;

    private int processId;

    private Long workflowDefinitionCode;

    private int workflowDefinitionVersion;

    private String appIds;

    private int workflowInstanceId;

    private String workflowInstanceName;

    private Long projectCode;

    private long scheduleTime;

    private String globalParams;

    private int executorId;

    private String tenantCode;

    private int workflowDefinitionId;

    private String taskParams;

    private String environmentConfig;

    /**
     * Include local params, global params, varpool transport from successors, start-up params and system built-in params
     */
    private Map<String, Property> prepareParamsMap;

    // Please use task instanceId
    @Deprecated
    private String taskAppId;

    private TaskTimeoutStrategy taskTimeoutStrategy;

    private int taskTimeout;

    private String workerGroup;

    private ResourceParametersHelper resourceParametersHelper;

    private long endTime;

    private SQLTaskExecutionContext sqlTaskExecutionContext;

    private ResourceContext resourceContext;

    private List<Property> varPool;

    private int dryRun;

    private Integer cpuQuota;

    private Integer memoryMax;

    private int dispatchFailTimes;

    private final long firstDispatchTime = System.currentTimeMillis();

    public int increaseDispatchFailTimes() {
        return ++dispatchFailTimes;
    }
}
