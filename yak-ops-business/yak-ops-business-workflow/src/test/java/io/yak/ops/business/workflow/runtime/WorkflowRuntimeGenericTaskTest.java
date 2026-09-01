package io.yak.ops.business.workflow.runtime;

import io.yak.ops.business.workflow.observability.WorkflowEventStream;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.framework.workflow.engine.support.InMemoryExecutionRepository;
import io.yak.framework.workflow.engine.support.InMemoryWorkflowDefinitionRepository;
import io.yak.ops.business.job.task.TaskDefinition;
import io.yak.ops.business.job.task.TaskExecution;
import io.yak.ops.business.job.task.TaskExecutionGateway;
import io.yak.ops.business.job.task.TaskExecutor;
import io.yak.ops.business.job.task.TaskRegistry;
import io.yak.ops.business.job.task.TaskVersionSnapshot;
import io.yak.ops.business.workflow.domain.WorkflowNodeSpec;
import io.yak.ops.business.workflow.domain.WorkflowRunSpec;
import io.yak.ops.business.workflow.repository.InMemoryWorkflowRuntimeRepository;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WorkflowRuntimeGenericTaskTest {

  private WorkflowRuntime service;

  @AfterEach
  void tearDown() {
    if (service != null) service.shutdown();
  }

  @Test
  void shouldExecuteNonSyncTaskWhenExecutorIsRegistered() throws InterruptedException {
    TaskRegistry registry = new TaskRegistry() {
      @Override
      public List<TaskDefinition> list() {
        return List.of(new TaskDefinition("SQL:1", "用户清洗 SQL", "SQL"));
      }

      @Override
      public TaskDefinition get(String taskId) {
        return list().getFirst();
      }

      @Override
      public TaskVersionSnapshot snapshot(String taskId) {
        return new TaskVersionSnapshot(
            taskId, "用户清洗 SQL", "SQL", 3L, "digest", "{}", "{}");
      }
    };
    TaskExecutionGateway gateway = new TaskExecutionGateway(List.of(new ImmediateSqlExecutor()));
    service = new WorkflowRuntime(
        new WorkflowEventStream(),
        registry,
        gateway,
        2L,
        new InMemoryWorkflowDefinitionRepository(),
        new InMemoryExecutionRepository(),
        new InMemoryWorkflowRuntimeRepository());

    WorkflowInstanceVO started = service.run(new WorkflowRunSpec(
        "sql-workflow",
        List.of(new WorkflowNodeSpec(
            "sql-node",
            "SQL:1",
            0D,
            0D,
            1,
            0L,
            0L,
            0L,
            Map.of("biz_date", "$workflow.biz_date"),
            "ALL_SUCCESS",
            "FAIL_WORKFLOW")),
        List.of(),
        Map.of("biz_date", "2026-08-10"),
        0L,
        "CONTINUE_INDEPENDENT_BRANCHES"));
    service.activate(started.id());

    WorkflowInstanceVO completed = waitForTerminal(started.id());
    WorkflowInstanceVO.NodeInstanceVO node = completed.nodes().getFirst();
    assertThat(completed.status()).isEqualTo("SUCCESS");
    assertThat(node.type()).isEqualTo("SQL");
    assertThat(node.output()).containsEntry("taskType", "SQL");
    assertThat(node.output()).containsEntry("taskExecutionId", "sql-execution-1");
  }

  private WorkflowInstanceVO waitForTerminal(String executionId) throws InterruptedException {
    for (int i = 0; i < 200; i++) {
      WorkflowInstanceVO current = service.getInstance(executionId);
      if ("SUCCESS".equals(current.status()) || "FAILED".equals(current.status())) return current;
      Thread.sleep(5L);
    }
    return service.getInstance(executionId);
  }

  private static final class ImmediateSqlExecutor implements TaskExecutor {
    @Override
    public String taskType() {
      return "SQL";
    }

    @Override
    public TaskExecution start(
        TaskVersionSnapshot snapshot,
        String idempotencyKey,
        Map<String, Object> input) {
      return new TaskExecution(
          "sql-execution-1", "SUCCEEDED", null, Map.of("received", input));
    }

    @Override
    public TaskExecution status(String executionId) {
      return new TaskExecution(executionId, "SUCCEEDED", null, Map.of());
    }

    @Override
    public void cancel(String executionId) {}
  }
}
