package io.yak.ops.server.master.engine.workflow.execution;

import io.yak.ops.server.master.runner.WorkflowExecuteContext;

import org.springframework.context.ApplicationContext;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WorkflowExecutionBuilder {

    private WorkflowExecuteContext.WorkflowExecuteContextBuilder workflowExecuteContextBuilder;

    private ApplicationContext applicationContext;

}
