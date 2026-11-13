package io.yak.ops.api.controller;

import io.yak.ops.api.service.ProjectService;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.dao.entity.User;
import io.yak.ops.modeling.api.vo.ModelingBootstrapVO;
import io.yak.ops.modeling.application.ModelingModuleService;

import org.springframework.beans.factory.annotation.Autowired;
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

/**
 * HTTP boundary for the data modeling product.
 */
@Tag(name = "DATA_MODELING_TAG")
@RestController
@RequestMapping("projects/{projectCode}/modeling")
public class ModelingController extends BaseController {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ModelingModuleService modelingModuleService;

    @Operation(summary = "queryModelingBootstrap", description = "QUERY_MODELING_BOOTSTRAP_NOTES")
    @GetMapping("/bootstrap")
    @ResponseStatus(HttpStatus.OK)
    public Result<ModelingBootstrapVO> queryBootstrap(
                                                      @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                                      @PathVariable("projectCode") long projectCode) {
        projectService.checkProjectAndAuthThrowException(loginUser, projectCode, null);
        return Result.success(modelingModuleService.getBootstrap(projectCode));
    }
}
