package io.yak.ops.security.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.security.authentication.AuthenticationManager;
import io.yak.ops.security.common.dto.user.UserPasswordResetDTO;
import io.yak.ops.security.common.entity.user.User;
import io.yak.ops.security.common.enums.ResultCode;
import io.yak.ops.security.common.po.UserPO;
import io.yak.ops.security.dao.UserDao;
import io.yak.ops.security.dao.mapper.UserMapper;
import io.yak.ops.security.exception.YakSecurityException;
import io.yak.ops.security.extend.PasswordEncoder;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 用户管理补充服务。
 *
 * <p>承载不适合通过完整用户编辑接口完成的管理操作，避免密码重置时
 * 覆盖用户资料、角色等并发变更。</p>
 *
 * @author weifuwan
 */
@Service
public class UserAdministrationService {

  private static final Logger LOGGER =
          LoggerFactory.getLogger(UserAdministrationService.class);

  private static final int MIN_PASSWORD_LENGTH = 8;
  private static final int MAX_PASSWORD_LENGTH = 64;

  private final UserDao userDao;
  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;
  private final ObjectProvider<AuthenticationManager>
          authenticationManagerProvider;

  public UserAdministrationService(
          UserDao userDao,
          UserMapper userMapper,
          PasswordEncoder passwordEncoder,
          ObjectProvider<AuthenticationManager> authenticationManagerProvider) {

    this.userDao = userDao;
    this.userMapper = userMapper;
    this.passwordEncoder = passwordEncoder;
    this.authenticationManagerProvider = authenticationManagerProvider;
  }

  /**
   * 校验当前用户不能删除自己。
   *
   * @param targetUserId 待删除用户 ID
   * @param operatorId 当前登录用户 ID
   * @param operator 当前登录用户名
   */
  public void validateDelete(
          Long targetUserId,
          Long operatorId,
          String operator) {

    if (targetUserId == null) {
      throw new YakSecurityException(
              ResultCode.USER_ID_CANNOT_BE_NULL);
    }

    User targetUser =
            userDao.selectByUserId(targetUserId);

    if (targetUser == null) {
      throw new YakSecurityException(
              ResultCode.USER_NOT_EXISTS);
    }

    boolean deletingSelfById =
            operatorId != null
                    && Objects.equals(
                    targetUserId,
                    operatorId);

    boolean deletingSelfByName =
            StringUtils.hasText(operator)
                    && Objects.equals(
                    targetUser.getUserName(),
                    operator);

    if (deletingSelfById || deletingSelfByName) {
      throw new YakSecurityException(
              "不能删除当前登录用户");
    }
  }

  /**
   * 管理员重置指定用户密码。
   *
   * <p>密码写入成功后立即注销该账号当前已登记的 HttpSession 登录态。</p>
   *
   * @param userId 用户 ID
   * @param request 重置密码请求
   * @param operator 操作人
   */
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void resetPassword(
          Long userId,
          UserPasswordResetDTO request,
          String operator) {

    if (userId == null) {
      throw new YakSecurityException(
              ResultCode.USER_ID_CANNOT_BE_NULL);
    }

    String password =
            request == null
                    ? null
                    : request.getPassword();

    if (!StringUtils.hasText(password)) {
      throw new YakSecurityException(
              "新密码不能为空");
    }

    if (password.length() < MIN_PASSWORD_LENGTH
            || password.length() > MAX_PASSWORD_LENGTH) {

      throw new YakSecurityException(
              "密码长度必须为 8～64 位");
    }

    User user = userDao.selectByUserId(userId);
    if (user == null) {
      throw new YakSecurityException(
              ResultCode.USER_NOT_EXISTS);
    }

    String encodedPassword =
            passwordEncoder.encode(password);

    int affectedRows =
            userMapper.update(
                    null,
                    Wrappers.<UserPO>lambdaUpdate()
                            .eq(UserPO::getId, userId)
                            .set(UserPO::getPw, encodedPassword));

    if (affectedRows != 1) {
      throw new YakSecurityException(
              ResultCode.USER_ACCOUNT_UPDATE_FAIL);
    }

    invalidateUserSessions(userId);

    LOGGER.info(
            "管理员重置用户密码成功，用户ID={}，用户名={}，操作人={}",
            userId,
            user.getUserName(),
            operator);
  }

  /**
   * 完整用户编辑接口修改密码后的登录态治理。
   *
   * <p>调用方仅应在密码字段确实发生更新且数据库写入成功后调用。</p>
   *
   * @param username 用户名
   * @param operator 操作人
   */
  public void invalidateSessionsAfterPasswordChange(
          String username,
          String operator) {

    if (!StringUtils.hasText(username)) {
      return;
    }

    User user = userDao.selectByUsername(username);
    if (user == null || user.getId() == null) {
      return;
    }

    invalidateUserSessions(user.getId());

    LOGGER.info(
            "用户密码变更后清理登录态，用户ID={}，用户名={}，操作人={}",
            user.getId(),
            user.getUserName(),
            operator);
  }

  /**
   * 管理员强制下线指定账号的全部登录终端。
   *
   * @param userId 用户 ID
   * @param operator 操作人
   */
  public void forceLogout(
          Long userId,
          String operator) {

    if (userId == null) {
      throw new YakSecurityException(
              ResultCode.USER_ID_CANNOT_BE_NULL);
    }

    User user = userDao.selectByUserId(userId);
    if (user == null) {
      throw new YakSecurityException(
              ResultCode.USER_NOT_EXISTS);
    }

    AuthenticationManager authenticationManager =
            authenticationManagerProvider.getIfAvailable();
    if (authenticationManager == null) {
      throw new YakSecurityException(
              "当前认证模式不支持账号级强制下线");
    }

    authenticationManager.logoutUser(userId);

    LOGGER.info(
            "管理员强制下线用户，用户ID={}，用户名={}，操作人={}",
            userId,
            user.getUserName(),
            operator);
  }

  private void invalidateUserSessions(Long userId) {
    AuthenticationManager authenticationManager =
            authenticationManagerProvider.getIfAvailable();
    if (authenticationManager != null) {
      authenticationManager.logoutUser(userId);
    }
  }
}
