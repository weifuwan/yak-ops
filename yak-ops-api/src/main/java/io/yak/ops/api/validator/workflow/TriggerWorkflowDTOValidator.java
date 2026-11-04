package io.yak.ops.api.validator.workflow;

import io.yak.ops.api.validator.IValidator;
import io.yak.ops.api.validator.StartParamListValidator;
import io.yak.ops.api.validator.TenantExistValidator;
import io.yak.ops.common.enums.CommandType;
import io.yak.ops.common.enums.ReleaseState;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TriggerWorkflowDTOValidator implements IValidator<TriggerWorkflowDTO> {

    private final TenantExistValidator tenantExistValidator;

    private final StartParamListValidator startParamListValidator;

    public TriggerWorkflowDTOValidator(TenantExistValidator tenantExistValidator,
                                       StartParamListValidator startParamListValidator) {
        this.tenantExistValidator = tenantExistValidator;
        this.startParamListValidator = startParamListValidator;
    }

    @Override
    public void validate(final TriggerWorkflowDTO triggerWorkflowDTO) {
        if (triggerWorkflowDTO.getExecType() != CommandType.START_PROCESS) {
            throw new IllegalArgumentException("The execType should be START_PROCESS");
        }
        if (triggerWorkflowDTO.getWorkflowDefinition() == null) {
            throw new IllegalArgumentException("The workflowDefinition should not be null");
        }
        if (triggerWorkflowDTO.getWorkflowDefinition().getReleaseState() != ReleaseState.ONLINE) {
            throw new IllegalStateException("The workflowDefinition should be online");
        }

        tenantExistValidator.validate(triggerWorkflowDTO.getTenantCode());

        startParamListValidator.validate(triggerWorkflowDTO.getStartParamList());
    }
}
