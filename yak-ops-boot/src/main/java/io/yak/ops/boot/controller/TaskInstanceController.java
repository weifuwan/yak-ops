package io.yak.ops.boot.controller;

import io.yak.ops.business.task.instance.InstanceService;
import io.yak.ops.common.bean.dto.task.InstancePageDTO;
import io.yak.ops.common.bean.vo.task.AttemptVO;
import io.yak.ops.common.bean.vo.task.InstanceVO;
import io.yak.ops.common.constant.CommonConstants;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.common.result.Result;
import io.yak.ops.dao.entity.task.AttemptEntity;
import io.yak.ops.dao.entity.task.InstanceEntity;
import io.yak.ops.task.execution.TaskExecutionService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Task execution observations and cancellation requests within the caller's Workspace. */
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/task-instances")
public class TaskInstanceController {

    @Resource
    private InstanceService instances;

    @Resource
    private TaskExecutionService execution;

    @GetMapping("/{id}")
    public Result<InstanceVO> detail(@PathVariable String id) {
        return Result.success(toInstance(instances.instance(WorkspaceContext.requireWorkspaceId(), id)));
    }

    @PostMapping("/page")
    public Result<PagingData<InstanceVO>> page(@Valid @RequestBody InstancePageDTO query) {
        if (!query.getSorts().isEmpty()) {
            throw new IllegalArgumentException("Custom Task Instance sorting is not supported");
        }
        return Result.success(PagingData.from(instances
                .page(WorkspaceContext.requireWorkspaceId(), query.getPageNo(), query.getPageSize())
                .map(this::toInstance)));
    }

    @GetMapping("/{id}/attempts")
    public Result<List<AttemptVO>> attempts(@PathVariable String id) {
        return Result.success(instances.attempts(WorkspaceContext.requireWorkspaceId(), id).stream()
                .map(this::toAttempt)
                .toList());
    }

    @PostMapping("/{id}/cancel")
    public Result<String> cancel(@PathVariable String id) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        return Result.success(execution.cancel(workspaceId, id).name());
    }

    private InstanceVO toInstance(InstanceEntity value) {
        return new InstanceVO(
                value.getId(),
                value.getDefinitionId(),
                value.getDefinitionVersion(),
                value.getTaskType(),
                value.getStatus().name(),
                value.getStartTime(),
                value.getEndTime(),
                value.getFailureCode(),
                value.getCreateTime());
    }

    private AttemptVO toAttempt(AttemptEntity value) {
        return new AttemptVO(
                value.getId(),
                value.getAttemptNumber(),
                value.getStatus().name(),
                value.getRuntimeEngine(),
                value.getRuntimeId(),
                value.getLogReference(),
                value.getFailureCode(),
                value.getStartTime(),
                value.getEndTime());
    }
}
