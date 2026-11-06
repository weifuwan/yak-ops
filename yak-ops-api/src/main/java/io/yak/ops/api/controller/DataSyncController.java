package io.yak.ops.api.controller;

import io.yak.ops.api.dto.datasync.DataSyncTaskRequest;
import io.yak.ops.api.service.DataSyncService;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.api.utils.Result;
import io.yak.ops.api.vo.DataSyncDefinitionVO;
import io.yak.ops.api.vo.DataSyncInstanceVO;
import io.yak.ops.api.vo.DataSyncTaskVO;
import io.yak.ops.api.vo.DataSyncTaskVersionVO;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.dao.entity.User;

import java.util.Collections;
import java.util.List;
import java.util.Map;

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

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/projects/{projectCode}/data-sync")
public class DataSyncController extends BaseController {

    private final DataSyncService dataSyncService;

    @GetMapping("/tasks")
    public Result<PageInfo<DataSyncTaskVO>> listTasks(
                                                      @RequestAttribute(Constants.SESSION_USER) User user,
                                                      @PathVariable long projectCode,
                                                      @RequestParam(defaultValue = "1") int pageNo,
                                                      @RequestParam(defaultValue = "20") int pageSize,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) String status) {
        checkPageParams(pageNo, pageSize);
        return Result.success(dataSyncService.listTasks(user, projectCode, pageNo, pageSize, keyword, status));
    }

    @GetMapping("/tasks/{taskCode}")
    public Result<DataSyncTaskVO> getTask(@RequestAttribute(Constants.SESSION_USER) User user,
                                          @PathVariable long projectCode, @PathVariable long taskCode) {
        return Result.success(dataSyncService.getTask(user, projectCode, taskCode));
    }

    @PostMapping("/tasks")
    public Result<DataSyncTaskVO> createTask(@RequestAttribute(Constants.SESSION_USER) User user,
                                             @PathVariable long projectCode,
                                             @RequestBody DataSyncTaskRequest request) {
        return Result.success(dataSyncService.createTask(user, projectCode, request));
    }

    @PutMapping("/tasks/{taskCode}")
    public Result<DataSyncTaskVO> updateTask(@RequestAttribute(Constants.SESSION_USER) User user,
                                             @PathVariable long projectCode, @PathVariable long taskCode,
                                             @RequestBody DataSyncTaskRequest request) {
        return Result.success(dataSyncService.updateTask(user, projectCode, taskCode, request));
    }

    @PostMapping("/tasks/{taskCode}/publish")
    public Result<DataSyncTaskVO> publish(@RequestAttribute(Constants.SESSION_USER) User user,
                                          @PathVariable long projectCode, @PathVariable long taskCode) {
        return Result.success(dataSyncService.publish(user, projectCode, taskCode));
    }

    @PostMapping("/tasks/{taskCode}/unpublish")
    public Result<DataSyncTaskVO> unpublish(@RequestAttribute(Constants.SESSION_USER) User user,
                                            @PathVariable long projectCode, @PathVariable long taskCode) {
        return Result.success(dataSyncService.unpublish(user, projectCode, taskCode));
    }

    @DeleteMapping("/tasks/{taskCode}")
    public Result<Void> deleteTask(@RequestAttribute(Constants.SESSION_USER) User user,
                                   @PathVariable long projectCode, @PathVariable long taskCode) {
        dataSyncService.deleteTask(user, projectCode, taskCode);
        return Result.success();
    }

    @GetMapping("/tasks/{taskCode}/versions")
    public Result<PageInfo<DataSyncTaskVersionVO>> versions(
                                                            @RequestAttribute(Constants.SESSION_USER) User user,
                                                            @PathVariable long projectCode,
                                                            @PathVariable long taskCode,
                                                            @RequestParam(defaultValue = "1") int pageNo,
                                                            @RequestParam(defaultValue = "20") int pageSize) {
        checkPageParams(pageNo, pageSize);
        return Result.success(dataSyncService.listVersions(user, projectCode, taskCode, pageNo, pageSize));
    }

    @GetMapping("/tasks/{taskCode}/versions/{version}")
    public Result<DataSyncTaskVersionVO> version(
                                                 @RequestAttribute(Constants.SESSION_USER) User user,
                                                 @PathVariable long projectCode,
                                                 @PathVariable long taskCode, @PathVariable int version) {
        return Result.success(dataSyncService.getVersion(user, projectCode, taskCode, version));
    }

    @PostMapping("/tasks/{taskCode}/start")
    public Result<Map<String, Integer>> start(@RequestAttribute(Constants.SESSION_USER) User user,
                                              @PathVariable long projectCode, @PathVariable long taskCode) {
        return Result.success(Collections.singletonMap("workflowInstanceId", dataSyncService.start(user, projectCode, taskCode)));
    }

    @GetMapping("/instances")
    public Result<PageInfo<DataSyncInstanceVO>> instances(
                                                          @RequestAttribute(Constants.SESSION_USER) User user,
                                                          @PathVariable long projectCode,
                                                          @RequestParam(defaultValue = "1") int pageNo,
                                                          @RequestParam(defaultValue = "20") int pageSize,
                                                          @RequestParam(required = false) Long taskCode,
                                                          @RequestParam(required = false) String status) {
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

    @PostMapping("/instances/{id}/stop")
    public Result<Void> stop(@RequestAttribute(Constants.SESSION_USER) User user,
                             @PathVariable long projectCode, @PathVariable int id) {
        dataSyncService.stop(user, projectCode, id);
        return Result.success();
    }

    @GetMapping("/instances/{id}/logs")
    public Result<Map<String, Object>> logs(@RequestAttribute(Constants.SESSION_USER) User user,
                                            @PathVariable long projectCode, @PathVariable int id,
                                            @RequestParam(defaultValue = "0") int offset,
                                            @RequestParam(defaultValue = "200") int limit) {
        return Result.success(dataSyncService.logs(user, projectCode, id, offset, limit));
    }
}
