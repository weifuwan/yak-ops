package io.yak.ops.api.validator.workflow;

import io.yak.ops.api.dto.workflow.WorkflowBackFillRequest;
import io.yak.ops.api.exceptions.ServiceException;
import io.yak.ops.api.utils.WorkflowUtils;
import io.yak.ops.api.validator.ITransformer;
import io.yak.ops.common.utils.CodeGenerateUtils;
import io.yak.ops.common.utils.DateUtils;
import io.yak.ops.dao.entity.Schedule;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.dao.repository.WorkflowDefinitionDao;
import io.yak.ops.plugin.task.api.utils.PropertyUtils;
import io.yak.ops.service.cron.CronUtils;
import io.yak.ops.service.process.ProcessService;

import org.apache.commons.lang3.StringUtils;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.SneakyThrows;

@Component
public class BackfillWorkflowRequestTransformer implements ITransformer<WorkflowBackFillRequest, BackfillWorkflowDTO> {

    @Autowired
    private ProcessService processService;

    @Autowired
    private WorkflowDefinitionDao workflowDefinitionDao;

    @Override
    public BackfillWorkflowDTO transform(WorkflowBackFillRequest workflowBackFillRequest) {

        final BackfillWorkflowDTO.BackfillParamsDTO backfillParams =
                transformBackfillParamsDTO(workflowBackFillRequest);
        final BackfillWorkflowDTO backfillWorkflowDTO = BackfillWorkflowDTO.builder()
                .loginUser(workflowBackFillRequest.getLoginUser())
                .startNodes(WorkflowUtils.parseStartNodeList(workflowBackFillRequest.getStartNodes()))
                .failureStrategy(workflowBackFillRequest.getFailureStrategy())
                .taskDependType(workflowBackFillRequest.getTaskDependType())
                .execType(workflowBackFillRequest.getExecType())
                .warningType(workflowBackFillRequest.getWarningType())
                .warningGroupId(workflowBackFillRequest.getWarningGroupId())
                .runMode(workflowBackFillRequest.getBackfillRunMode())
                .workflowInstancePriority(workflowBackFillRequest.getWorkflowInstancePriority())
                .workerGroup(workflowBackFillRequest.getWorkerGroup())
                .tenantCode(workflowBackFillRequest.getTenantCode())
                .environmentCode(workflowBackFillRequest.getEnvironmentCode())
                .startParamList(
                        PropertyUtils.startParamsTransformPropertyList(workflowBackFillRequest.getStartParamList()))
                .dryRun(workflowBackFillRequest.getDryRun())
                .triggerCode(CodeGenerateUtils.genCode())
                .backfillParams(backfillParams)
                .build();

        WorkflowDefinition workflowDefinition = workflowDefinitionDao
                .queryByCode(workflowBackFillRequest.getWorkflowDefinitionCode())
                .orElseThrow(() -> new ServiceException(
                        "Cannot find the workflow: " + workflowBackFillRequest.getWorkflowDefinitionCode()));

        backfillWorkflowDTO.setWorkflowDefinition(workflowDefinition);
        return backfillWorkflowDTO;
    }

    private BackfillWorkflowDTO.BackfillParamsDTO transformBackfillParamsDTO(WorkflowBackFillRequest workflowBackFillRequest) {
        final List<ZonedDateTime> backfillDateList = parseBackfillDateList(workflowBackFillRequest);
        return BackfillWorkflowDTO.BackfillParamsDTO.builder()
                .runMode(workflowBackFillRequest.getBackfillRunMode())
                .expectedParallelismNumber(workflowBackFillRequest.getExpectedParallelismNumber())
                .backfillDateList(backfillDateList)
                .backfillDependentMode(workflowBackFillRequest.getBackfillDependentMode())
                .allLevelDependent(workflowBackFillRequest.isAllLevelDependent())
                .executionOrder(workflowBackFillRequest.getExecutionOrder())
                .build();
    }

    @SneakyThrows
    private List<ZonedDateTime> parseBackfillDateList(WorkflowBackFillRequest workflowBackFillRequest) {
        final WorkflowBackFillRequest.BackfillTime backfillTime = workflowBackFillRequest.getBackfillTime();
        List<Schedule> schedules = processService.queryReleaseSchedulerListByWorkflowDefinitionCode(
                workflowBackFillRequest.getWorkflowDefinitionCode());

        if (StringUtils.isNotEmpty(backfillTime.getComplementStartDate())
                && StringUtils.isNotEmpty(backfillTime.getComplementEndDate())) {
            // todo: why we need to filter the schedules here?
            return CronUtils.getSelfFireDateList(
                    DateUtils.stringToZoneDateTime(backfillTime.getComplementStartDate()),
                    DateUtils.stringToZoneDateTime(backfillTime.getComplementEndDate()),
                    schedules);
        }
        if (StringUtils.isNotEmpty(backfillTime.getComplementScheduleDateList())) {
            return Arrays.stream(backfillTime.getComplementScheduleDateList().split(","))
                    .distinct()
                    .map(DateUtils::stringToZoneDateTime)
                    .collect(Collectors.toList());
        }
        throw new ServiceException("backfillTime: " + backfillTime + " is invalid");
    }
}
