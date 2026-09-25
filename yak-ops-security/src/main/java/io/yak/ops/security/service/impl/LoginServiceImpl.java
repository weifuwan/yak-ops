package io.yak.ops.security.service.impl;

import io.yak.ops.common.bean.dto.security.account.AccountLoginDTO;
import io.yak.ops.common.bean.vo.security.user.UserBriefVO;
import io.yak.ops.common.enums.security.ResultCode;
import io.yak.ops.common.enums.security.user.UserStatus;
import io.yak.ops.common.exception.YakSecurityException;
import io.yak.ops.common.result.Result;
import io.yak.ops.common.util.BeanCopyUtils;
import io.yak.ops.common.util.CollectionUtils;
import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.common.util.ObjectUtils;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.security.authentication.AuthenticationManager;
import io.yak.ops.security.authentication.LoginAttemptGuard;
import io.yak.ops.security.config.YakSecurityProperties;
import io.yak.ops.security.constant.SecurityConstants;
import io.yak.ops.security.extend.PasswordEncoder;
import io.yak.ops.security.model.UserAccount;
import io.yak.ops.security.service.LoginService;
import io.yak.ops.security.service.UserService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

/** Default login behavior and authenticated-request validation. */
@ConditionalOnProperty(
        prefix = SecurityConstants.CONFIG_PREFIX,
        name = {"enabled", "database-enabled"},
        havingValue = "true",
        matchIfMissing = true)
@Service
public class LoginServiceImpl implements LoginService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoginServiceImpl.class);
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    @Resource
    private UserService userService;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private AuthenticationManager authenticationManager;

    @Resource
    private YakSecurityProperties properties;

    private LoginAttemptGuard loginAttemptGuard;
    private YakSecurityProperties.LoginSecurityProperties loginProperties;

    @PostConstruct
    void initializeLoginSecurity() {
        loginProperties = properties.getLogin();
        loginAttemptGuard = new LoginAttemptGuard(loginProperties);
    }

    @Override
    public UserBriefVO verifyLogin(AccountLoginDTO loginDTO, HttpServletRequest request) {
        validateLoginParam(loginDTO, request);
        String userName = loginDTO.getUserName().trim();
        String remoteAddress = request.getRemoteAddr();
        if (loginAttemptGuard.isBlocked(userName, remoteAddress)) {
            throw new YakSecurityException(ResultCode.USER_ACCOUNT_LOCKED);
        }

        UserAccount user = userService.getUserByUsername(userName);
        if (ObjectUtils.isNull(user)) {
            loginAttemptGuard.recordFailure(userName, remoteAddress);
            throw new YakSecurityException(
                    loginProperties.isHideAccountNotFound()
                            ? ResultCode.USER_CREDENTIALS_ERROR
                            : ResultCode.USER_NOT_EXISTS);
        }
        if (UserStatus.DISABLED.equals(user.getStatus())) {
            throw new YakSecurityException(ResultCode.USER_ACCOUNT_DISABLE);
        }
        if (!passwordEncoder.matches(loginDTO.getPw(), user.getPw())) {
            loginAttemptGuard.recordFailure(userName, remoteAddress);
            throw new YakSecurityException(ResultCode.USER_CREDENTIALS_ERROR);
        }
        if (ObjectUtils.isNull(user.getId())) {
            LOGGER.error("登录用户缺少用户 ID，userName={}", userName);
            throw new IllegalStateException("Login user id must not be null");
        }

        authenticationManager.login(user.getId(), userName);
        loginAttemptGuard.recordSuccess(userName, remoteAddress);
        return BeanCopyUtils.copy(user, UserBriefVO.class);
    }

    @Override
    public Result<Boolean> logout() {
        authenticationManager.logout();
        return Result.success(Boolean.TRUE);
    }

    @Override
    public boolean interceptorCheck(HttpServletResponse response, String requestPath, List<String> whiteListPatterns)
            throws IOException {
        if (StringUtils.isBlank(requestPath)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return false;
        }
        if (isWhiteListPath(requestPath, whiteListPatterns)) {
            return true;
        }
        if (!authenticationManager.isLogin()) {
            return handleUnauthorized(response);
        }

        String loginUserId = authenticationManager.getLoginUserId();
        String operator = authenticationManager.getLoginUsername();
        if (ObjectUtils.isNull(loginUserId) || StringUtils.isBlank(operator)) {
            authenticationManager.logout();
            return handleUnauthorized(response);
        }

        UserAccount user = userService.getUserByUsername(operator);
        if (ObjectUtils.isNull(user)
                || UserStatus.DISABLED.equals(user.getStatus())
                || !Objects.equals(loginUserId, user.getId())) {
            LOGGER.warn("登录态失效，operator={}, loginUserId={}", operator, loginUserId);
            authenticationManager.logout();
            return handleUnauthorized(response);
        }
        return true;
    }

    private boolean handleUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write(JSONUtils.toJson(Result.fail(ResultCode.USER_NOT_LOGIN)));
        return false;
    }

    private boolean isWhiteListPath(String requestPath, List<String> whiteListPatterns) {
        if (CollectionUtils.isEmpty(whiteListPatterns)) {
            return false;
        }
        for (String pattern : whiteListPatterns) {
            if (StringUtils.isNotBlank(pattern) && PATH_MATCHER.match(pattern.trim(), requestPath)) {
                return true;
            }
        }
        return false;
    }

    private void validateLoginParam(AccountLoginDTO loginDTO, HttpServletRequest request) {
        if (ObjectUtils.isNull(loginDTO)
                || ObjectUtils.isNull(request)
                || StringUtils.isBlank(loginDTO.getUserName())
                || StringUtils.isBlank(loginDTO.getPw())) {
            throw new YakSecurityException(ResultCode.PARAM_NOT_VALID);
        }
    }
}
