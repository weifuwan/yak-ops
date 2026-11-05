package io.yak.ops.api.controller;

import io.yak.ops.api.dto.UserPreferenceFavoriteRequest;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.common.enums.UserPreferenceScene;
import io.yak.ops.common.model.UserPreferenceRecord;
import io.yak.ops.dao.entity.User;
import io.yak.ops.service.preference.UserPreferenceService;

import java.util.List;

import javax.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "USER_PREFERENCE_TAG")
@RestController
@RequestMapping("/user-preferences")
public class UserPreferenceController {

    private final UserPreferenceService service;

    public UserPreferenceController(UserPreferenceService service) {
        this.service = service;
    }

    @GetMapping
    public Result<List<UserPreferenceRecord>> list(
                                                   @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                                   @RequestParam("scene") UserPreferenceScene scene) {
        return Result.success(service.list(scene, loginUser.getId()));
    }

    @PutMapping("/{scene}/{itemKey}/favorite")
    public Result<UserPreferenceRecord> favorite(
                                                 @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                                 @PathVariable("scene") UserPreferenceScene scene,
                                                 @PathVariable("itemKey") String itemKey,
                                                 @Valid @RequestBody UserPreferenceFavoriteRequest body) {
        return Result.success(service.updateFavorite(scene, itemKey, body.getFavorite(), loginUser.getId()));
    }

    @PostMapping("/{scene}/{itemKey}/use")
    public Result<UserPreferenceRecord> use(
                                            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                            @PathVariable("scene") UserPreferenceScene scene,
                                            @PathVariable("itemKey") String itemKey) {
        return Result.success(service.recordUsage(scene, itemKey, loginUser.getId()));
    }
}
