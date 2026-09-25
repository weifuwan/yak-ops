package io.yak.ops.security.service.impl;

import io.yak.ops.common.bean.dto.security.user.UserPasswordResetDTO;
import io.yak.ops.common.enums.security.ResultCode;
import io.yak.ops.common.exception.YakSecurityException;
import io.yak.ops.dao.entity.security.UserEntity;
import io.yak.ops.dao.repository.security.UserRepository;
import io.yak.ops.security.authentication.AuthenticationManager;
import io.yak.ops.security.constant.SecurityConstants;
import io.yak.ops.security.extend.PasswordEncoder;
import jakarta.annotation.Resource;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** User administration operations that require focused persistence updates. */
@ConditionalOnProperty(
        prefix = SecurityConstants.CONFIG_PREFIX,
        name = {"enabled", "database-enabled"},
        havingValue = "true",
        matchIfMissing = true)
@Service
public class UserAdministrationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserAdministrationService.class);
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 64;

    @Resource
    private UserRepository userRepository;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private ObjectProvider<AuthenticationManager> authenticationManagerProvider;

    public void validateDelete(String targetUserId, String operatorId, String operator) {
        if (targetUserId == null) {
            throw new YakSecurityException(ResultCode.USER_ID_CANNOT_BE_NULL);
        }

        UserEntity targetUser = userRepository.queryById(targetUserId).orElse(null);
        if (targetUser == null) {
            throw new YakSecurityException(ResultCode.USER_NOT_EXISTS);
        }

        boolean deletingSelfById = operatorId != null && Objects.equals(targetUserId, operatorId);
        boolean deletingSelfByName =
                StringUtils.hasText(operator) && Objects.equals(targetUser.getUserName(), operator);

        if (deletingSelfById || deletingSelfByName) {
            throw new YakSecurityException("不能删除当前登录用户");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(String userId, UserPasswordResetDTO request, String operator) {
        if (userId == null) {
            throw new YakSecurityException(ResultCode.USER_ID_CANNOT_BE_NULL);
        }

        String password = request == null ? null : request.getPassword();
        if (!StringUtils.hasText(password)) {
            throw new YakSecurityException("新密码不能为空");
        }
        if (password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
            throw new YakSecurityException("密码长度必须为 8～64 位");
        }

        UserEntity user = userRepository.queryById(userId).orElse(null);
        if (user == null) {
            throw new YakSecurityException(ResultCode.USER_NOT_EXISTS);
        }

        String encodedPassword = passwordEncoder.encode(password);
        user.setPw(encodedPassword);
        user.initUpdate(operator);
        userRepository.update(user);

        invalidateUserSessions(userId);
        LOGGER.info("管理员重置用户密码成功，用户ID={}，用户名={}，操作人={}", userId, user.getUserName(), operator);
    }

    public void invalidateSessionsAfterPasswordChange(String username, String operator) {
        if (!StringUtils.hasText(username)) return;

        UserEntity user = userRepository.queryByUsername(username).orElse(null);
        if (user == null || user.getId() == null) return;

        invalidateUserSessions(user.getId());
        LOGGER.info("用户密码变更后清理登录态，用户ID={}，用户名={}，操作人={}", user.getId(), user.getUserName(), operator);
    }

    public void forceLogout(String userId, String operator) {
        if (userId == null) {
            throw new YakSecurityException(ResultCode.USER_ID_CANNOT_BE_NULL);
        }

        UserEntity user = userRepository.queryById(userId).orElse(null);
        if (user == null) {
            throw new YakSecurityException(ResultCode.USER_NOT_EXISTS);
        }

        AuthenticationManager authenticationManager = authenticationManagerProvider.getIfAvailable();
        if (authenticationManager == null) {
            throw new YakSecurityException("当前认证模式不支持账号级强制下线");
        }

        authenticationManager.logoutUser(userId);
        LOGGER.info("管理员强制下线用户，用户ID={}，用户名={}，操作人={}", userId, user.getUserName(), operator);
    }

    private void invalidateUserSessions(String userId) {
        AuthenticationManager authenticationManager = authenticationManagerProvider.getIfAvailable();
        if (authenticationManager != null) authenticationManager.logoutUser(userId);
    }
}
