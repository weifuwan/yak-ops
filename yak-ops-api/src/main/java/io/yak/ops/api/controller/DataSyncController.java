package io.yak.ops.api.controller;

import io.yak.ops.api.audit.OperatorLog;
import io.yak.ops.api.audit.enums.AuditType;
import io.yak.ops.api.dto.datasync.DataSyncPublishRequest;
import io.yak.ops.api.dto.datasync.DataSyncTaskRequest;
import io.yak.ops.api.service.DataSyncService;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.api.utils.Result;
import io.yak.ops.api.vo.DataSyncDefinitionVO;
import io.yak.ops.api.vo.DataSyncOverviewVO;
import io.yak.ops.api.vo.DataSyncInstanceVO;
import io.yak.ops.api.vo.DataSyncTaskVO;
import io.yak.ops.api.vo.DataSyncTaskVersionVO;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.common.enums.ReleaseState;
import io.yak.ops.dao.entity.User;
import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/projects/{projectCode}/data-sync")
public class DataSyncController extends BaseController {

    private final DataSyncService dataSyncService;

    @GetMapping("/overview")
    public Result<DataSyncOverviewVO> overview(@RequestAttribute(Constants.SESSION_USER) User user,
                                               @PathVariable long projectCode) {
        return Result.success(dataSyncService.getOverview(user, projectCode));
    }

    @GetMapping("/tasks")
    public Result<PageInfo<DataSyncTaskVO>> listTasks(
                                                      @RequestAttribute(Constants.SESSION_USER) User user,
                                                      @PathVariable long projectCode,
                                                      @RequestParam(defaultValue = "1") int pageNo,
                                                      @RequestParam(defaultValue = "20") int pageSize,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) ReleaseState status) {
        checkPageParams(pageNo, pageSize);
        return Result.success(dataSyncService.listTasks(user, projectCode, pageNo, pageSize, keyword, status));
    }

    @GetMapping("/tasks/{taskCode}")
    public Result<DataSyncTaskVO> getTask(@RequestAttribute(Constants.SESSION_USER) User user,
                                          @PathVariable long projectCode, @PathVariable long taskCode) {
        return Result.success(dataSyncService.getTask(user, projectCode, taskCode));
    }

    @PostMapping("/tasks/preview")
    @Operation(summary = "previewDataSyncTask", description = "Preview data synchronization pipeline YAML")
    public Result<String> preview(@RequestAttribute(Constants.SESSION_USER) User user,
                                  @PathVariable long projectCode,
                                  @RequestBody DataSyncTaskRequest request) {
        return Result.success(dataSyncService.preview(user, projectCode, request));
    }

    @PostMapping("/tasks")
    @Operation(summary = "createDataSyncTask", description = "Create data synchronization task")
    @OperatorLog(auditType = AuditType.DATA_SYNC_CREATE)
    public Result<DataSyncTaskVO> createTask(@RequestAttribute(Constants.SESSION_USER) User user,
                                             @PathVariable long projectCode,
                                             @RequestBody DataSyncTaskRequest request) {
        return Result.success(dataSyncService.createTask(user, projectCode, request));
    }

    @PutMapping("/tasks/{taskCode}")
    @Operation(summary = "updateDataSyncTask", description = "Update data synchronization task")
    @OperatorLog(auditType = AuditType.DATA_SYNC_UPDATE)
    public Result<DataSyncTaskVO> updateTask(@RequestAttribute(Constants.SESSION_USER) User user,
                                             @PathVariable long projectCode, @PathVariable long taskCode,
                                             @RequestBody DataSyncTaskRequest request) {
        return Result.success(dataSyncService.updateTask(user, projectCode, taskCode, request));
    }

    @PostMapping("/tasks/{taskCode}/publish")
    @Operation(summary = "publishDataSyncTask", description = "Publish data synchronization task")
    @OperatorLog(auditType = AuditType.DATA_SYNC_PUBLISH)
    public Result<DataSyncTaskVO> publish(@RequestAttribute(Constants.SESSION_USER) User user,
                                          @PathVariable long projectCode, @PathVariable long taskCode,
                                          @RequestBody DataSyncPublishRequest request) {
        return Result.success(dataSyncService.publish(user, projectCode, taskCode, request));
    }

    @PostMapping("/tasks/{taskCode}/unpublish")
    @Operation(summary = "unpublishDataSyncTask", description = "Unpublish data synchronization task")
    @OperatorLog(auditType = AuditType.DATA_SYNC_UNPUBLISH)
    public Result<DataSyncTaskVO> unpublish(@RequestAttribute(Constants.SESSION_USER) User user,
                                            @PathVariable long projectCode, @PathVariable long taskCode) {
        return Result.success(dataSyncService.unpublish(user, projectCode, taskCode));
    }

    @DeleteMapping("/tasks/{taskCode}")
    @Operation(summary = "deleteDataSyncTask", description = "Delete data synchronization task")
    @OperatorLog(auditType = AuditType.DATA_SYNC_DELETE)
    public Result<Void> deleteTask(@RequestAttribute(Constants.SESSION_USER) User user,
                                   @PathVariable long projectCode, @PathVariable long taskCode) {
        dataSyncService.deleteTask(user, projectCode, taskCode);
        return Result.success();
    }

    @GetMapping("/tasks/{taskCode}/versions/{version}")
    public Result<DataSyncTaskVersionVO> version(
                                                 @RequestAttribute(Constants.SESSION_USER) User user,
                                                 @PathVariable long projectCode,
                                                 @PathVariable long taskCode, @PathVariable int version) {
        return Result.success(dataSyncService.getVersion(user, projectCode, taskCode, version));
    }

    @GetMapping("/instances")
    public Result<PageInfo<DataSyncInstanceVO>> instances(
                                                          @RequestAttribute(Constants.SESSION_USER) User user,
                                                          @PathVariable long projectCode,
                                                          @RequestParam(defaultValue = "1") int pageNo,
                                                          @RequestParam(defaultValue = "20") int pageSize,
                                                          @RequestParam(required = false) Long taskCode,
                                                          @RequestParam(required = false) TaskExecutionStatus status) {
        checkPageParams(pageNo, pageSize);
        return Result.success(dataSyncService.listInstances(user, projectCode, pageNo, pageSize, taskCode, status));
    }

    @GetMapping("/instances/latest")
    public Result<List<DataSyncInstanceVO>> latest(
                                                   @RequestAttribute(Constants.SESSION_USER) User user,
                                                   @PathVariable long projectCode,
                                                   @RequestParam List<Long> taskCodes,
                                                   @RequestParam(defaultValue = "false") boolean activeOnly) {
        return Result.success(dataSyncService.latestInstances(user, projectCode, taskCodes, activeOnly));
    }

    @GetMapping("/instances/{id}")
    public Result<DataSyncInstanceVO> getInstance(@RequestAttribute(Constants.SESSION_USER) User user,
                                                  @PathVariable long projectCode, @PathVariable int id) {
        return Result.success(dataSyncService.getInstance(user, projectCode, id));
    }

    @GetMapping("/instances/{id}/definition")
    public Result<DataSyncDefinitionVO> getInstanceDefinition(@RequestAttribute(Constants.SESSION_USER) User user,
                                                              @PathVariable long projectCode, @PathVariable int id) {
        return Result.success(dataSyncService.getInstanceDefinition(user, projectCode, id));
    }

}
