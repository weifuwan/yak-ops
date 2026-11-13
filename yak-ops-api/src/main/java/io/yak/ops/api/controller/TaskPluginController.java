package io.yak.ops.api.controller;

import static io.yak.ops.api.enums.Status.LIST_TASK_TYPE_ERROR;

import io.yak.ops.api.configuration.TaskTypeConfiguration;
import io.yak.ops.api.dto.TaskPluginTypeDto;
import io.yak.ops.api.enums.Status;
import io.yak.ops.api.exceptions.ApiException;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.dao.entity.User;
import io.yak.ops.plugin.task.api.TaskPluginManager;

import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "TASK_PLUGIN")
@RestController
@RequestMapping("/task-plugins")
public class TaskPluginController extends BaseController {

    @Resource
    private TaskTypeConfiguration taskTypeConfiguration;

    @Operation(summary = "listRegisteredTaskTypes", description = "LIST_REGISTERED_TASK_TYPES")
    @GetMapping("/types")
    @ResponseStatus(HttpStatus.OK)
    @ApiException(LIST_TASK_TYPE_ERROR)
    public Result<List<TaskPluginTypeDto>> listRegisteredTaskTypes(
                                                                   @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser) {
        List<TaskPluginTypeDto> taskTypes = TaskPluginManager.getRegisteredTaskTypes()
                .stream()
                .map(taskType -> new TaskPluginTypeDto(
                        taskType,
                        taskTypeConfiguration.getTaskCategory(taskType)))
                .collect(Collectors.toList());
        return success(Status.SUCCESS.getMsg(), taskTypes);
    }
}
