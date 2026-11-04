package io.yak.ops.dao.entity;

import io.yak.ops.plugin.task.api.enums.DependResult;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class DependentResultTaskInstanceContext extends AbstractTaskInstanceContext {

    private Long projectCode;

    private Long workflowDefinitionCode;

    private Long taskDefinitionCode;

    private String dateCycle;

    private DependResult dependentResult;

}
