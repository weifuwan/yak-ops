package io.yak.ops.security.authentication;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionBindingListener;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 基于 Servlet HttpSession 的登录态实现。
 *
 * <p>不引入额外 Token 框架。登录后由容器通过 JSESSIONID Cookie 维持会话。</p>
 */
public final class HttpSessionAuthenticationManager
        implements AuthenticationManager {

  private static final String USER_ID_KEY =
          "yak-security:user-id";
  private static final String USERNAME_KEY =
          "yak-security:username";
  private static final String REGISTRATION_KEY =
          "yak-security:registration";

  private final int maxInactiveIntervalSeconds;
  private final ConcurrentMap<Long, Set<HttpSession>>
          sessionsByUser = new ConcurrentHashMap<>();

  public HttpSessionAuthenticationManager(
          Duration idleTimeout) {
    Objects.requireNonNull(
            idleTimeout,
            "idleTimeout must not be null");

    long seconds = idleTimeout.getSeconds();
    if (seconds < 1 || seconds > Integer.MAX_VALUE) {
      throw new IllegalArgumentException(
              "idleTimeout must be between 1 second and Integer.MAX_VALUE seconds");
    }

    this.maxInactiveIntervalSeconds = (int) seconds;
  }

  @Override
  public void login(
          Long userId,
          String userName) {
    Objects.requireNonNull(
            userId,
            "userId must not be null");
    if (!StringUtils.hasText(userName)) {
      throw new IllegalArgumentException(
              "userName must not be blank");
    }

    HttpServletRequest request = requireRequest();
    HttpSession previous = request.getSession(false);
    if (previous != null) {
      invalidate(previous);
    }

    HttpSession session = request.getSession(true);
    session.setMaxInactiveInterval(
            maxInactiveIntervalSeconds);
    session.setAttribute(USER_ID_KEY, userId);
    session.setAttribute(USERNAME_KEY, userName);
    session.setAttribute(
            REGISTRATION_KEY,
            new SessionRegistration(this, userId));
  }

  @Override
  public void logout() {
    HttpServletRequest request = currentRequest();
    if (request == null) {
      return;
    }
    HttpSession session = request.getSession(false);
    if (session != null) {
      invalidate(session);
    }
  }

  @Override
  public void logoutUser(Long userId) {
    if (userId == null) {
      return;
    }

    Set<HttpSession> sessions =
            sessionsByUser.remove(userId);
    if (sessions == null || sessions.isEmpty()) {
      return;
    }

    for (HttpSession session
            : new ArrayList<>(sessions)) {
      invalidate(session);
    }
  }

  @Override
  public boolean isLogin() {
    HttpSession session = currentSession();
    return session != null
            && readUserId(session) != null
            && StringUtils.hasText(
                    readUsername(session));
  }

  @Override
  public Long getLoginUserId() {
    HttpSession session = currentSession();
    return session == null
            ? null
            : readUserId(session);
  }

  @Override
  public String getLoginUsername() {
    HttpSession session = currentSession();
    return session == null
            ? null
            : readUsername(session);
  }

  private HttpSession currentSession() {
    HttpServletRequest request = currentRequest();
    return request == null
            ? null
            : request.getSession(false);
  }

  private HttpServletRequest requireRequest() {
    HttpServletRequest request = currentRequest();
    if (request == null) {
      throw new IllegalStateException(
              "No servlet request is bound to the current thread");
    }
    return request;
  }

  private HttpServletRequest currentRequest() {
    if (!(RequestContextHolder.getRequestAttributes()
            instanceof ServletRequestAttributes attributes)) {
      return null;
    }
    return attributes.getRequest();
  }

  private Long readUserId(HttpSession session) {
    try {
      Object value = session.getAttribute(USER_ID_KEY);
      return value instanceof Number number
              ? number.longValue()
              : null;
    } catch (IllegalStateException ignored) {
      return null;
    }
  }

  private String readUsername(HttpSession session) {
    try {
      Object value =
              session.getAttribute(USERNAME_KEY);
      return value instanceof String text
              && StringUtils.hasText(text)
              ? text
              : null;
    } catch (IllegalStateException ignored) {
      return null;
    }
  }

  private void register(
          Long userId,
          HttpSession session) {
    sessionsByUser
            .computeIfAbsent(
                    userId,
                    ignored ->
                            ConcurrentHashMap.newKeySet())
            .add(session);
  }

  private void unregister(
          Long userId,
          HttpSession session) {
    Set<HttpSession> sessions =
            sessionsByUser.get(userId);
    if (sessions == null) {
      return;
    }

    sessions.remove(session);
    if (sessions.isEmpty()) {
      sessionsByUser.remove(
              userId,
              sessions);
    }
  }

  private void invalidate(HttpSession session) {
    try {
      session.invalidate();
    } catch (IllegalStateException ignored) {
      // Session has already expired or been invalidated.
    }
  }

  private static final class SessionRegistration
          implements HttpSessionBindingListener {

    private final HttpSessionAuthenticationManager manager;
    private final Long userId;

    private SessionRegistration(
            HttpSessionAuthenticationManager manager,
            Long userId) {
      this.manager = manager;
      this.userId = userId;
    }

    @Override
    public void valueBound(
            HttpSessionBindingEvent event) {
      manager.register(
              userId,
              event.getSession());
    }

    @Override
    public void valueUnbound(
            HttpSessionBindingEvent event) {
      manager.unregister(
              userId,
              event.getSession());
    }
  }
}
