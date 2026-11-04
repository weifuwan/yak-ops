package io.yak.ops.dao.utils;

import io.yak.ops.common.enums.WorkflowExecutionStatus;
import io.yak.ops.common.utils.DateUtils;
import io.yak.ops.dao.entity.WorkflowInstance;

import java.util.Date;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WorkflowUtilsTest {

    @Test
    public void testGetWorkflowInstanceDuration() {
        WorkflowInstance workflowInstance = new WorkflowInstance();
        workflowInstance.setId(1);
        workflowInstance.setState(null);
        Date start = DateUtils.stringToDate("2020-01-20 11:00:00");
        Date end = DateUtils.stringToDate("2020-01-21 12:10:10");
        workflowInstance.setStartTime(start);
        workflowInstance.setEndTime(end);

        String noStateDuration = WorkflowUtils.getWorkflowInstanceDuration(workflowInstance);
        System.currentTimeMillis();
        Assertions.assertNotEquals("1d 1h 10m 10s", noStateDuration);

        workflowInstance.setState(WorkflowExecutionStatus.RUNNING_EXECUTION);
        String notFinishDuration = WorkflowUtils.getWorkflowInstanceDuration(workflowInstance);
        Assertions.assertNotEquals("1d 1h 10m 10s", notFinishDuration);

        workflowInstance.setState(WorkflowExecutionStatus.SUCCESS);
        String successDuration = WorkflowUtils.getWorkflowInstanceDuration(workflowInstance);
        Assertions.assertEquals("1d 1h 10m 10s", successDuration);
    }
}
