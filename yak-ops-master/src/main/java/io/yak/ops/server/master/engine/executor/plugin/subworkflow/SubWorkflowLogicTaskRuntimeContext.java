package io.yak.ops.server.master.engine.executor.plugin.subworkflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The runtime context used to store the {@link SubWorkflowLogicTask} runtime information.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubWorkflowLogicTaskRuntimeContext {

    private Integer subWorkflowInstanceId;

    public static SubWorkflowLogicTaskRuntimeContext of(final Integer subWorkflowInstanceId) {
        return SubWorkflowLogicTaskRuntimeContext.builder()
                .subWorkflowInstanceId(subWorkflowInstanceId)
                .build();
    }

}
