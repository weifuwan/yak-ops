package io.yak.ops.api.controller;

import static io.yak.ops.api.enums.Status.QUERY_PROJECT_PREFERENCE_ERROR;
import static io.yak.ops.api.enums.Status.UPDATE_PROJECT_PREFERENCE_ERROR;
import static io.yak.ops.api.enums.Status.UPDATE_PROJECT_PREFERENCE_STATE_ERROR;

import io.yak.ops.api.exceptions.ApiException;
import io.yak.ops.api.service.ProjectPreferenceService;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.dao.entity.User;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@Tag(name = "PROJECT_PREFERENCE_TAG")
@RestController
@RequestMapping("projects/{projectCode}/project-preference")
@Slf4j
public class ProjectPreferenceController extends BaseController {

    @Autowired
    private ProjectPreferenceService projectPreferenceService;

    @Operation(summary = "updateProjectPreference", description = "UPDATE_PROJECT_PREFERENCE_NOTES")
    @Parameters({
            @Parameter(name = "projectPreferences", description = "PROJECT_PREFERENCES", schema = @Schema(implementation = String.class)),
    })
    @PutMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ApiException(UPDATE_PROJECT_PREFERENCE_ERROR)
    public Result updateProjectPreference(@Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                          @Parameter(name = "projectCode", description = "PROJECT_CODE", required = true) @PathVariable long projectCode,
                                          @RequestParam(value = "projectPreferences", required = true) String projectPreferences) {
        return projectPreferenceService.updateProjectPreference(loginUser, projectCode, projectPreferences);
    }

    @Operation(summary = "queryProjectPreferenceByProjectCode", description = "QUERY_PROJECT_PREFERENCE_NOTES")
    @GetMapping()
    @ResponseStatus(HttpStatus.OK)
    @ApiException(QUERY_PROJECT_PREFERENCE_ERROR)
    public Result queryProjectPreferenceByProjectCode(@Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                                      @Parameter(name = "projectCode", description = "PROJECT_CODE", required = true) @PathVariable long projectCode) {
        return projectPreferenceService.queryProjectPreferenceByProjectCode(loginUser, projectCode);
    }

    @Operation(summary = "enableProjectPreference", description = "UPDATE_PROJECT_PREFERENCE_STATE_NOTES")
    @Parameters({
            @Parameter(name = "state", description = "PROJECT_PREFERENCES_STATE", schema = @Schema(implementation = String.class)),
    })
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    @ApiException(UPDATE_PROJECT_PREFERENCE_STATE_ERROR)
    public Result enableProjectPreference(@Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                          @Parameter(name = "projectCode", description = "PROJECT_CODE", required = true) @PathVariable long projectCode,
                                          @RequestParam(value = "state", required = true) int state) {
        return projectPreferenceService.enableProjectPreference(loginUser, projectCode, state);
    }

}
