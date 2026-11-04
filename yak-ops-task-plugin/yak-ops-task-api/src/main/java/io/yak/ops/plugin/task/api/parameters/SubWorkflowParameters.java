package io.yak.ops.plugin.task.api.parameters;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class SubWorkflowParameters extends AbstractParameters {

    private long workflowDefinitionCode;

    @Override
    public boolean checkParameters() {
        return this.workflowDefinitionCode > 0;
    }

}
