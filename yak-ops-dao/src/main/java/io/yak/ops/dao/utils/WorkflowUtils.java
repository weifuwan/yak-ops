package io.yak.ops.dao.utils;

import io.yak.ops.common.utils.DateUtils;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.dao.model.WorkflowInstanceSummaryDto;

import java.util.Date;

public class WorkflowUtils {

    /**
     * get workflow duration
     * if processInstance is running, the endTime will be the current time
     *
     * @param workflowInstance workflow instance
     * @return workflow duration
     */
    public static String getWorkflowInstanceDuration(WorkflowInstance workflowInstance) {
        return workflowInstance.getState() != null && workflowInstance.getState().isFinalState()
                ? DateUtils.format2Duration(workflowInstance.getStartTime(), workflowInstance.getEndTime())
                : DateUtils.format2Duration(workflowInstance.getStartTime(), new Date());
    }

    /**
     * get workflow duration from a {@link WorkflowInstanceSummaryDto}
     *
     * @param workflowInstanceSummaryDto workflow instance summary dto
     * @return workflow duration
     */
    public static String getWorkflowInstanceDuration(WorkflowInstanceSummaryDto workflowInstanceSummaryDto) {
        return workflowInstanceSummaryDto.getState() != null
                && workflowInstanceSummaryDto.getState().isFinalState()
                        ? DateUtils.format2Duration(workflowInstanceSummaryDto.getStartTime(),
                                workflowInstanceSummaryDto.getEndTime())
                        : DateUtils.format2Duration(workflowInstanceSummaryDto.getStartTime(), new Date());
    }

}
