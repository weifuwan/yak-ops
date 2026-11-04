package io.yak.ops.api.controller;

import static io.yak.ops.api.enums.Status.LIST_TASK_TYPE_ERROR;

import io.yak.ops.api.configuration.DynamicTaskTypeConfiguration;
import io.yak.ops.api.dto.taskType.DynamicTaskInfo;
import io.yak.ops.api.enums.Status;
import io.yak.ops.api.exceptions.ApiException;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.dao.entity.User;

import java.util.List;

import javax.annotation.Resource;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "DYNAMIC_TASK_TYPE")
@RestController
@RequestMapping("/dynamic")
public class DynamicTaskTypeController extends BaseController {

    @Resource
    private DynamicTaskTypeConfiguration dynamicTaskTypeConfiguration;

    /**
     * get dynamic task category list
     *
     * @param loginUser login user
     * @return dynamic task category list
     */
    @Operation(summary = "listTaskCategories", description = "LIST_TASK_TYPE_CATEGORIES")
    @GetMapping(value = "/taskCategories")
    @ResponseStatus(HttpStatus.OK)
    @ApiException(LIST_TASK_TYPE_ERROR)
    public Result listDynamicTaskCategories(@Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser) {
        List<String> taskCategories = dynamicTaskTypeConfiguration.getTaskCategories();
        return success(Status.SUCCESS.getMsg(), taskCategories);
    }

    /**
     * get dynamic task category list
     *
     * @param loginUser login user
     * @return dynamic task category list
     */
    @Operation(summary = "listDynamicTaskTypes", description = "LIST_DYNAMIC_TASK_TYPES")
    @GetMapping(value = "/{taskCategory}/taskTypes")
    @ResponseStatus(HttpStatus.OK)
    @ApiException(LIST_TASK_TYPE_ERROR)
    public Result listDynamicTaskTypes(@Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                       @PathVariable("taskCategory") String taskCategory) {
        List<DynamicTaskInfo> taskTypes = dynamicTaskTypeConfiguration.getTaskTypesByCategory(taskCategory);
        return success(Status.SUCCESS.getMsg(), taskTypes);
    }

}
