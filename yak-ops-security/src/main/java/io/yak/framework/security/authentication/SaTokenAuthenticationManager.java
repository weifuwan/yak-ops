package io.yak.framework.security.authentication;

import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import java.time.Duration;
import java.util.Objects;
import org.springframework.util.StringUtils;

/**
 * 基于 Sa-Token 的登录态管理适配器。
 *
 * <p>Sa-Token 被限制在该适配层内，上层 Yak Security 与业务模块无需直接使用 StpUtil 或 StpLogic。</p>
 */
public class SaTokenAuthenticationManager
        implements AuthenticationManager {

  private static final String USERNAME_KEY =
          "yak-security:username";

  private final StpLogic stpLogic;
  private final Long activeTimeoutSeconds;

  public SaTokenAuthenticationManager(StpLogic stpLogic) {
    this(stpLogic, null);
  }

  public SaTokenAuthenticationManager(
          StpLogic stpLogic,
          Duration activeTimeout) {

    this.stpLogic = Objects.requireNonNull(
            stpLogic,
            "stpLogic must not be null");

    if (activeTimeout == null) {
      this.activeTimeoutSeconds = null;
      return;
    }

    long seconds = activeTimeout.getSeconds();
    if (seconds < 1) {
      throw new IllegalArgumentException(
              "activeTimeout must be greater than 0 seconds");
    }

    this.activeTimeoutSeconds = seconds;
    SaTokenConfig config = this.stpLogic.getConfigOrGlobal();
    config.setDynamicActiveTimeout(true);
  }

  @Override
  public void login(Long userId) {
    Objects.requireNonNull(userId, "userId must not be null");

    if (activeTimeoutSeconds == null) {
      stpLogic.login(userId);
      return;
    }

    SaLoginParameter loginParameter =
            stpLogic.createSaLoginParameter()
                    .setActiveTimeout(activeTimeoutSeconds)
                    .setIsLastingCookie(false);

    stpLogic.login(userId, loginParameter);
  }

  @Override
  public void login(Long userId, String userName) {
    if (!StringUtils.hasText(userName)) {
      throw new IllegalArgumentException("userName must not be blank");
    }

    login(userId);
    SaSession tokenSession = stpLogic.getTokenSession(true);
    tokenSession.set(USERNAME_KEY, userName);
  }

  @Override
  public void logout() {
    stpLogic.logout();
  }

  @Override
  public void logoutUser(Long userId) {
    Objects.requireNonNull(userId, "userId must not be null");
    stpLogic.logout(userId);
  }

  @Override
  public boolean isLogin() {
    return stpLogic.isLogin();
  }

  @Override
  public Long getLoginUserId() {
    Object loginId = stpLogic.getLoginIdDefaultNull();
    if (loginId == null) {
      return null;
    }
    if (loginId instanceof Number number) {
      return number.longValue();
    }
    if (loginId instanceof CharSequence sequence) {
      String value = sequence.toString().trim();
      if (value.isEmpty()) {
        throw invalidLoginId(loginId, null);
      }
      try {
        return Long.valueOf(value);
      } catch (NumberFormatException exception) {
        throw invalidLoginId(loginId, exception);
      }
    }
    throw invalidLoginId(loginId, null);
  }

  @Override
  public String getLoginUsername() {
    if (!stpLogic.isLogin()) {
      return null;
    }
    SaSession tokenSession = stpLogic.getTokenSession(false);
    if (tokenSession == null) {
      return null;
    }
    Object value = tokenSession.get(USERNAME_KEY);
    return value instanceof String text && StringUtils.hasText(text)
            ? text
            : null;
  }

  private IllegalStateException invalidLoginId(
          Object loginId,
          Exception cause) {
    String message =
            "Sa-Token loginId must be convertible to Long, actual type="
                    + loginId.getClass().getName();
    return cause == null
            ? new IllegalStateException(message)
            : new IllegalStateException(message, cause);
  }
}
