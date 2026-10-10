package io.yak.ops.boot.controller.datasync.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.ops.business.datasync.SyncDefinitionService;
import io.yak.ops.common.bean.dto.datasync.DataSyncTaskDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncTaskQueryDTO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTaskVO;
import io.yak.ops.common.constant.CommonConstants;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.common.result.Result;
import io.yak.ops.security.authentication.AuthenticationManager;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DATA_SYNC 插件当前单表定义的创建、查询和发布入口，URL 行为保持兼容。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Tag(name = "数据同步任务定义")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/data-sync")
public class DefinitionController {

    @Resource
    private SyncDefinitionService taskService;

    @Resource
    private AuthenticationManager authenticationManager;

    @Operation(summary = "创建数据同步任务")
    @PostMapping("/tasks")
    public Result<DataSyncTaskVO> createTask(@Valid @RequestBody DataSyncTaskDTO dto) {
        return Result.success(taskService.createTask(dto, currentUserId()));
    }

    @Operation(summary = "编辑数据同步任务")
    @PutMapping("/tasks/{id}")
    public Result<DataSyncTaskVO> updateTask(@PathVariable("id") String id, @Valid @RequestBody DataSyncTaskDTO dto) {
        return Result.success(taskService.updateTask(id, dto, currentUserId()));
    }

    @Operation(summary = "查询数据同步任务详情")
    @GetMapping("/tasks/{id}")
    public Result<DataSyncTaskVO> taskDetail(@PathVariable("id") String id) {
        return Result.success(taskService.queryTask(id));
    }

    @Operation(summary = "分页查询数据同步任务")
    @PostMapping("/tasks/page")
    public Result<PagingData<DataSyncTaskVO>> taskPage(@Valid @RequestBody DataSyncTaskQueryDTO dto) {
        return Result.success(taskService.queryTaskPage(dto));
    }

    @Operation(summary = "上线数据同步任务")
    @PostMapping("/tasks/{id}/publish")
    public Result<DataSyncTaskVO> publishTask(@PathVariable("id") String id) {
        return Result.success(taskService.publishTask(id, currentUserId()));
    }

    @Operation(summary = "下线数据同步任务")
    @PostMapping("/tasks/{id}/unpublish")
    public Result<DataSyncTaskVO> unpublishTask(@PathVariable("id") String id) {
        return Result.success(taskService.unpublishTask(id, currentUserId()));
    }

    @Operation(summary = "删除数据同步任务")
    @DeleteMapping("/tasks/{id}")
    public Result<Boolean> deleteTask(@PathVariable("id") String id) {
        return Result.success(taskService.deleteTask(id));
    }

    private String currentUserId() {
        return authenticationManager.getLoginUserId();
    }
}
