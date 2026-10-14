package io.yak.ops.boot.controller;

import io.yak.ops.business.task.instance.InstanceService;
import io.yak.ops.common.bean.dto.task.InstanceLatestDTO;
import io.yak.ops.common.bean.dto.task.InstancePageDTO;
import io.yak.ops.common.bean.vo.task.InstanceDefinitionVO;
import io.yak.ops.common.bean.vo.task.InstanceVO;
import io.yak.ops.common.bean.vo.task.TaskLogPageVO;
import io.yak.ops.common.constant.CommonConstants;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.common.result.Result;
import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.dao.entity.task.InstanceEntity;
import io.yak.ops.task.execution.TaskExecutionService;
import io.yak.ops.task.execution.dispatch.embedded.TaskExecutionObserver;
import io.yak.ops.task.execution.log.TaskLogService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** Task execution observations and cancellation requests within the caller's Workspace. */
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/task-instances")
public class TaskInstanceController {

    @Resource
    private InstanceService instances;

    @Resource
    private TaskExecutionService execution;

    @Resource
    private TaskExecutionObserver observer;

    @Resource
    private TaskLogService logs;

    @GetMapping("/{id}")
    public Result<InstanceVO> detail(@PathVariable String id) {
        return Result.success(toInstance(instances.instance(WorkspaceContext.requireWorkspaceId(), id), true));
    }

    @GetMapping("/{id}/definition")
    public Result<InstanceDefinitionVO> definition(@PathVariable String id) {
        return Result.success(instances.definition(WorkspaceContext.requireWorkspaceId(), id));
    }

    @PostMapping("/page")
    public Result<PagingData<InstanceVO>> page(@Valid @RequestBody InstancePageDTO query) {
        if (!query.getSorts().isEmpty()) {
            throw new IllegalArgumentException("Custom Task Instance sorting is not supported");
        }
        return Result.success(PagingData.from(instances
                .page(
                        WorkspaceContext.requireWorkspaceId(),
                        query.getPageNo(),
                        query.getPageSize(),
                        query.getDefinitionId(),
                        query.getStatus(),
                        query.getTaskType())
                .map(this::toInstance)));
    }

    /** Reads the newest Instance of each visible Definition in a single Workspace-scoped operation. */
    @PostMapping("/latest")
    public Result<List<InstanceVO>> latest(@Valid @RequestBody InstanceLatestDTO query) {
        return Result.success(
                instances
                        .latest(WorkspaceContext.requireWorkspaceId(), query.getDefinitionIds(), query.isActiveOnly())
                        .stream()
                        .map(this::toInstance)
                        .toList());
    }

    /** Reads a bounded log page for a Workspace-owned Instance. */
    @GetMapping("/{id}/logs")
    public Result<TaskLogPageVO> logs(
            @PathVariable String id,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "200") int limit)
            throws IOException {
        return Result.success(logs.read(WorkspaceContext.requireWorkspaceId(), id, cursor, limit));
    }

    /** Streams retained Task logs under the same Instance ownership check. */
    @GetMapping("/{id}/logs/download")
    public ResponseEntity<StreamingResponseBody> downloadLog(@PathVariable String id) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        instances.instance(workspaceId, id);
        StreamingResponseBody body = out -> logs.download(workspaceId, id, out);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"task-" + id + ".log\"")
                .body(body);
    }

    @PostMapping("/{id}/cancel")
    public Result<String> cancel(@PathVariable String id) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        return Result.success(execution.cancel(workspaceId, id).name());
    }

    private String reportSummary(String report) {
        if (report == null) return null;
        try {
            var summary = JSONUtils.readTree(report).get("summary");
            if (summary == null || !summary.isObject()) return null;
            String json = summary.toString();
            if (json.length() > 16384) return null;
            return json;
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    private InstanceVO toInstance(InstanceEntity value) {
        return toInstance(value, false);
    }

    private InstanceVO toInstance(InstanceEntity value, boolean includeReport) {
        java.time.Instant scheduledTime = null;
        if (value.getScheduledTime() != null) {
            scheduledTime = value.getScheduledTime().toInstant(java.time.ZoneOffset.UTC);
        }
        Integer progress = value.getProgressPercent();
        if (value.getStatus() == io.yak.ops.common.enums.task.TaskInstanceStatus.FINISHED) {
            progress = 100;
        } else if (!value.getStatus().isTerminal()) {
            var live = observer.progressPercent(value.getId());
            if (live.isPresent()) {
                progress = live.getAsInt();
            }
        }
        String report = value.getExecutionReport();
        if (!includeReport) report = reportSummary(report);
        if (!value.getStatus().isTerminal()
                && value.getStatus() != io.yak.ops.common.enums.task.TaskInstanceStatus.RECONCILING) {
            if (includeReport) report = observer.executionReport(value.getId()).orElse(report);
            else report = observer.executionSummary(value.getId()).orElse(report);
        }
        return new InstanceVO(
                value.getId(),
                value.getDefinitionId(),
                value.getDefinitionVersion(),
                value.isDebug(),
                value.getTaskType(),
                value.getTriggerType().name(),
                value.getStatus().name(),
                value.getRuntimeEngine(),
                value.getRuntimeId(),
                value.getStartTime(),
                value.getEndTime(),
                value.getFailureCode(),
                value.getCreateTime(),
                value.getScheduleId(),
                value.getScheduleRevision(),
                scheduledTime,
                progress,
                report);
    }
}
