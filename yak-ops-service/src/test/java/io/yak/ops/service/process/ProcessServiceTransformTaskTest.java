package io.yak.ops.service.process;

import io.yak.ops.common.enums.Flag;
import io.yak.ops.common.enums.TimeoutFlag;
import io.yak.ops.dao.entity.TaskDefinitionLog;
import io.yak.ops.dao.entity.WorkflowTaskRelation;
import io.yak.ops.service.exceptions.ServiceException;

import java.util.Collections;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ProcessServiceTransformTaskTest {

    @Test
    void transformTaskShouldRejectMissingPreTaskDefinition() {
        WorkflowTaskRelation relation = new WorkflowTaskRelation();
        relation.setPreTaskCode(1001L);
        relation.setPreTaskVersion(1);
        relation.setPostTaskCode(1002L);
        relation.setPostTaskVersion(1);

        TaskDefinitionLog postTask = new TaskDefinitionLog();
        postTask.setCode(1002L);
        postTask.setVersion(1);
        postTask.setName("post-task");
        postTask.setTaskType("SHELL");
        postTask.setFlag(Flag.YES);
        postTask.setTaskParams("{}");
        postTask.setTimeoutFlag(TimeoutFlag.CLOSE);

        ProcessServiceImpl processService = new ProcessServiceImpl();

        ServiceException exception = Assertions.assertThrows(ServiceException.class,
                () -> processService.transformTask(
                        Collections.singletonList(relation),
                        Collections.singletonList(postTask)));

        Assertions.assertTrue(exception.getMessage().contains("1001"));
        Assertions.assertTrue(exception.getMessage().contains("1002"));
    }

    @Test
    void transformTaskShouldRejectMissingTaskType() {
        WorkflowTaskRelation relation = new WorkflowTaskRelation();
        relation.setPostTaskCode(1002L);
        relation.setPostTaskVersion(1);

        TaskDefinitionLog postTask = new TaskDefinitionLog();
        postTask.setCode(1002L);
        postTask.setVersion(1);
        postTask.setName("post-task");
        postTask.setFlag(Flag.YES);

        ProcessServiceImpl processService = new ProcessServiceImpl();

        ServiceException exception = Assertions.assertThrows(ServiceException.class,
                () -> processService.transformTask(
                        Collections.singletonList(relation),
                        Collections.singletonList(postTask)));

        Assertions.assertTrue(exception.getMessage().contains("taskType"));
        Assertions.assertTrue(exception.getMessage().contains("1002"));
    }
}
