package io.yak.ops.api.validator.workflow;

import io.yak.ops.api.validator.IValidator;
import io.yak.ops.api.validator.StartParamListValidator;
import io.yak.ops.api.validator.TenantExistValidator;
import io.yak.ops.common.enums.CommandType;
import io.yak.ops.common.enums.ReleaseState;

import org.apache.commons.collections4.CollectionUtils;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class BackfillWorkflowDTOValidator implements IValidator<BackfillWorkflowDTO> {

    private final TenantExistValidator tenantExistValidator;

    private final StartParamListValidator startParamListValidator;

    public BackfillWorkflowDTOValidator(TenantExistValidator tenantExistValidator,
                                        StartParamListValidator startParamListValidator) {
        this.tenantExistValidator = tenantExistValidator;
        this.startParamListValidator = startParamListValidator;
    }

    @Override
    public void validate(final BackfillWorkflowDTO backfillWorkflowDTO) {
        final BackfillWorkflowDTO.BackfillParamsDTO backfillParams = backfillWorkflowDTO.getBackfillParams();
        if (backfillParams == null) {
            throw new IllegalArgumentException("backfillParams is null");
        }
        if (CollectionUtils.isEmpty(backfillParams.getBackfillDateList())) {
            throw new IllegalArgumentException("backfillDateList is empty");
        }
        if (backfillParams.getExpectedParallelismNumber() < 0) {
            throw new IllegalArgumentException("expectedParallelismNumber should >= 0");
        }
        if (backfillWorkflowDTO.getExecType() != CommandType.COMPLEMENT_DATA) {
            throw new IllegalArgumentException("The execType should be START_PROCESS");
        }
        if (backfillWorkflowDTO.getWorkflowDefinition() == null) {
            throw new IllegalArgumentException("The workflowDefinition should not be null");
        }
        if (backfillWorkflowDTO.getWorkflowDefinition().getReleaseState() != ReleaseState.ONLINE) {
            throw new IllegalStateException("The workflowDefinition should be online");
        }

        tenantExistValidator.validate(backfillWorkflowDTO.getTenantCode());

        startParamListValidator.validate(backfillWorkflowDTO.getStartParamList());
    }
}
