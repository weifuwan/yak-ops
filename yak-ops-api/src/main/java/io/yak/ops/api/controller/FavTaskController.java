package io.yak.ops.api.controller;

import static io.yak.ops.api.enums.Status.ADD_TASK_TYPE_ERROR;
import static io.yak.ops.api.enums.Status.DELETE_TASK_TYPE_ERROR;
import static io.yak.ops.api.enums.Status.LIST_TASK_TYPE_ERROR;

import io.yak.ops.api.dto.FavTaskDto;
import io.yak.ops.api.enums.Status;
import io.yak.ops.api.exceptions.ApiException;
import io.yak.ops.api.service.FavTaskService;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.dao.entity.User;

import java.util.List;

import javax.annotation.Resource;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * fav controller
 */
@Tag(name = "FAVOURITE_TAG")
@RestController
@RequestMapping("/favourite")
public class FavTaskController extends BaseController {

    @Resource
    private FavTaskService favTaskService;

    /**
     * get task type list
     *
     * @param loginUser login user
     * @return task type list
     */
    @Operation(summary = "listTaskType", description = "QUERY_TASK_TYPE_LIST")
    @GetMapping(value = "/taskTypes")
    @ResponseStatus(HttpStatus.OK)
    @ApiException(LIST_TASK_TYPE_ERROR)
    public Result listTaskType(@Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser) {
        List<FavTaskDto> favTaskList = favTaskService.getFavTaskList(loginUser);
        return success(Status.SUCCESS.getMsg(), favTaskList);
    }

    /**
     * delete task fav
     *
     * @param loginUser login user
     * @return
     */
    @Operation(summary = "deleteTaskType", description = "DELETE_TASK_TYPE")
    @DeleteMapping(value = "/{taskType}")
    @ResponseStatus(HttpStatus.OK)
    @ApiException(DELETE_TASK_TYPE_ERROR)
    public Result deleteFavTask(@Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                @PathVariable("taskType") String taskType) {
        boolean b = favTaskService.deleteFavTask(loginUser, taskType);
        return success(b);
    }

    /**
     * add task fav
     *
     * @param loginUser login user
     * @return
     */
    @Operation(summary = "addTaskType", description = "ADD_TASK_TYPE")
    @PostMapping(value = "/{taskType}")
    @ResponseStatus(HttpStatus.OK)
    @ApiException(ADD_TASK_TYPE_ERROR)
    public Result addFavTask(@Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                             @PathVariable("taskType") String taskType) {
        int i = favTaskService.addFavTask(loginUser, taskType);
        return success(i > 0);
    }
}
