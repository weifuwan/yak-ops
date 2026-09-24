package io.yak.ops.boot.controller.security.v1;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.ops.common.PagingData;
import io.yak.ops.common.Result;
import io.yak.ops.common.bean.dto.security.user.UserDTO;
import io.yak.ops.common.bean.dto.security.user.UserPasswordResetDTO;
import io.yak.ops.common.bean.dto.security.user.UserQueryDTO;
import io.yak.ops.common.bean.vo.security.user.UserBriefVO;
import io.yak.ops.common.bean.vo.security.user.UserVO;
import io.yak.ops.common.enums.security.ResultCode;
import io.yak.ops.common.exception.YakSecurityException;
import io.yak.ops.security.authentication.AuthenticationManager;
import io.yak.ops.security.service.UserService;
import io.yak.ops.security.service.impl.UserAdministrationService;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 用户管理接口。 */
@ConditionalOnProperty(
        prefix = "yak.security",
        name = {"enabled", "database-enabled", "web-enabled"},
        havingValue = "true",
        matchIfMissing = true)
@Tag(name = "用户管理")
@RestController
@RequestMapping("/yak-security/api/v1/user")
public class UserController {

    private final UserService userService;
    private final UserAdministrationService userAdministrationService;
    private final AuthenticationManager authenticationManager;
    private final ObjectMapper objectMapper;

    public UserController(
            UserService userService,
            UserAdministrationService userAdministrationService,
            AuthenticationManager authenticationManager,
            ObjectMapper objectMapper) {
        this.userService = userService;
        this.userAdministrationService = userAdministrationService;
        this.authenticationManager = authenticationManager;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "校验用户字段是否可用")
    @GetMapping("/{type}/{value}/check")
    public Result<Void> check(@PathVariable Integer type, @PathVariable String value) {
        return userService.check(type, value);
    }

    @Operation(summary = "根据用户 ID 集合批量查询用户详情")
    @GetMapping
    public Result<List<UserVO>> detailList(@RequestParam("ids") String ids) {
        return userService.getUserDetailsByUserIds(parseUserIds(ids));
    }

    @Operation(summary = "根据用户 ID 查询用户详情")
    @GetMapping("/{id}")
    public Result<UserVO> detail(@PathVariable("id") Long userId) {
        return Result.success(userService.getUserDetailByUserId(userId));
    }

    @Operation(summary = "分页查询用户")
    @PostMapping("/page")
    public Result<PagingData<UserVO>> page(@RequestBody UserQueryDTO queryDTO) {
        return Result.success(userService.getUserPage(queryDTO));
    }

    @Operation(summary = "根据用户名或真实姓名模糊查询用户")
    @GetMapping("/list/{keyword}")
    public Result<List<UserBriefVO>> listByName(@PathVariable String keyword) {
        return Result.success(userService.searchUserBriefList(keyword));
    }

    @Operation(summary = "新增用户")
    @PutMapping("/add")
    public Result<Void> add(@RequestBody UserDTO userDTO) {
        return userService.addUser(userDTO, currentUsername());
    }

    @Operation(summary = "编辑用户")
    @PostMapping("/edit")
    public Result<Void> edit(@RequestBody UserDTO userDTO) {

        String operator = currentUsername();
        Result<Void> result = userService.editUser(userDTO, operator);

        if (!result.failed() && userDTO != null && StringUtils.hasText(userDTO.getPw())) {
            userAdministrationService.invalidateSessionsAfterPasswordChange(userDTO.getUserName(), operator);
        }

        return result;
    }

    @Operation(summary = "管理员重置用户密码")
    @PutMapping("/{id}/password")
    public Result<Void> resetPassword(@PathVariable("id") Long userId, @RequestBody UserPasswordResetDTO resetDTO) {

        userAdministrationService.resetPassword(userId, resetDTO, currentUsername());

        return Result.success();
    }

    @Operation(summary = "根据用户 ID 删除用户")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long userId) {

        userAdministrationService.validateDelete(userId, authenticationManager.getLoginUserId(), currentUsername());

        return userService.deleteByUserId(userId);
    }

    private List<Long> parseUserIds(String ids) {
        try {
            JavaType type = objectMapper.getTypeFactory().constructCollectionType(List.class, Long.class);
            return objectMapper.readValue(ids, type);
        } catch (JsonProcessingException exception) {
            throw new YakSecurityException(ResultCode.PARAM_NOT_VALID, exception);
        }
    }

    private String currentUsername() {
        return authenticationManager.getLoginUsername();
    }
}
