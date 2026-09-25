package io.yak.ops.security.authentication;

import io.yak.ops.common.util.CollectionUtils;
import io.yak.ops.common.util.ObjectUtils;
import io.yak.ops.common.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionBindingListener;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 基于 Servlet HttpSession 的登录态实现。
 *
 * <p>不引入额外 Token 框架。登录后由容器通过 JSESSIONID Cookie 维持会话。</p>
 */
public final class HttpSessionAuthenticationManager implements AuthenticationManager {

    private static final String USER_ID_KEY = "yak-security:user-id";
    private static final String USERNAME_KEY = "yak-security:username";
    private static final String REGISTRATION_KEY = "yak-security:registration";

    private final int maxInactiveIntervalSeconds;
    private final ConcurrentMap<String, Set<HttpSession>> sessionsByUser = new ConcurrentHashMap<>();

    public HttpSessionAuthenticationManager(Duration idleTimeout) {
        ObjectUtils.requireNonNull(idleTimeout, "idleTimeout must not be null");

        long seconds = idleTimeout.getSeconds();
        if (seconds < 1 || seconds > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("idleTimeout must be between 1 second and Integer.MAX_VALUE seconds");
        }

        this.maxInactiveIntervalSeconds = (int) seconds;
    }

    @Override
    public void login(String userId, String userName) {
        ObjectUtils.requireNonNull(userId, "userId must not be null");
        if (StringUtils.isBlank(userName)) {
            throw new IllegalArgumentException("userName must not be blank");
        }

        HttpServletRequest request = requireRequest();
        HttpSession previous = request.getSession(false);
        if (ObjectUtils.isNotNull(previous)) {
            invalidate(previous);
        }

        HttpSession session = request.getSession(true);
        session.setMaxInactiveInterval(maxInactiveIntervalSeconds);
        session.setAttribute(USER_ID_KEY, userId);
        session.setAttribute(USERNAME_KEY, userName);
        session.setAttribute(REGISTRATION_KEY, new SessionRegistration(this, userId));
    }

    @Override
    public void logout() {
        HttpServletRequest request = currentRequest();
        if (ObjectUtils.isNull(request)) {
            return;
        }
        HttpSession session = request.getSession(false);
        if (ObjectUtils.isNotNull(session)) {
            invalidate(session);
        }
    }

    @Override
    public void logoutUser(String userId) {
        if (ObjectUtils.isNull(userId)) {
            return;
        }

        Set<HttpSession> sessions = sessionsByUser.remove(userId);
        if (CollectionUtils.isEmpty(sessions)) {
            return;
        }

        for (HttpSession session : new ArrayList<>(sessions)) {
            invalidate(session);
        }
    }

    @Override
    public boolean isLogin() {
        HttpSession session = currentSession();
        return ObjectUtils.isNotNull(session) && readUserId(session) != null && StringUtils.isNotBlank(readUsername(session));
    }

    @Override
    public String getLoginUserId() {
        HttpSession session = currentSession();
        return ObjectUtils.isNull(session) ? null : readUserId(session);
    }

    @Override
    public String getLoginUsername() {
        HttpSession session = currentSession();
        return ObjectUtils.isNull(session) ? null : readUsername(session);
    }

    private HttpSession currentSession() {
        HttpServletRequest request = currentRequest();
        return ObjectUtils.isNull(request) ? null : request.getSession(false);
    }

    private HttpServletRequest requireRequest() {
        HttpServletRequest request = currentRequest();
        if (ObjectUtils.isNull(request)) {
            throw new IllegalStateException("No servlet request is bound to the current thread");
        }
        return request;
    }

    private HttpServletRequest currentRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        return attributes.getRequest();
    }

    private String readUserId(HttpSession session) {
        try {
            Object value = session.getAttribute(USER_ID_KEY);
            return value instanceof String text && StringUtils.isNotBlank(text) ? text : null;
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private String readUsername(HttpSession session) {
        try {
            Object value = session.getAttribute(USERNAME_KEY);
            return value instanceof String text && StringUtils.isNotBlank(text) ? text : null;
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private void register(String userId, HttpSession session) {
        sessionsByUser
                .computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet())
                .add(session);
    }

    private void unregister(String userId, HttpSession session) {
        Set<HttpSession> sessions = sessionsByUser.get(userId);
        if (ObjectUtils.isNull(sessions)) {
            return;
        }

        sessions.remove(session);
        if (sessions.isEmpty()) {
            sessionsByUser.remove(userId, sessions);
        }
    }

    private void invalidate(HttpSession session) {
        try {
            session.invalidate();
        } catch (IllegalStateException ignored) {
            // Session has already expired or been invalidated.
        }
    }

    private static final class SessionRegistration implements HttpSessionBindingListener {

        private final HttpSessionAuthenticationManager manager;
        private final String userId;

        private SessionRegistration(HttpSessionAuthenticationManager manager, String userId) {
            this.manager = manager;
            this.userId = userId;
        }

        @Override
        public void valueBound(HttpSessionBindingEvent event) {
            manager.register(userId, event.getSession());
        }

        @Override
        public void valueUnbound(HttpSessionBindingEvent event) {
            manager.unregister(userId, event.getSession());
        }
    }
}
