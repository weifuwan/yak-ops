package io.yak.ops.api.interceptor;

import io.yak.ops.api.enums.Status;
import io.yak.ops.api.metrics.ApiServerMetrics;
import io.yak.ops.api.security.Authenticator;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.common.enums.Flag;
import io.yak.ops.common.thread.ThreadLocalContext;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.repository.UserDao;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;

import java.util.Date;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import lombok.extern.slf4j.Slf4j;

/**
 * login interceptor, must log in first
 */
@Slf4j
public class LoginHandlerInterceptor implements HandlerInterceptor {

    @Autowired
    private UserDao userDao;

    @Autowired
    private Authenticator authenticator;

    /**
     * Intercept the execution of a handler. Called after HandlerMapping determined
     *
     * @param request current HTTP request
     * @param response current HTTP response
     * @param handler chosen handler to execute, for type and/or instance evaluation
     * @return boolean true or false
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        ApiServerMetrics.incApiRequestCount();

        // get token
        String token = request.getHeader("token");
        User user;
        if (StringUtils.isEmpty(token)) {
            user = authenticator.getAuthUser(request);
            // if user is null
            if (user == null) {
                response.setStatus(HttpStatus.SC_UNAUTHORIZED);
                log.info("user does not exist");
                return false;
            }
        } else {
            user = userDao.queryUserByToken(token, new Date());
            if (user == null) {
                response.setStatus(HttpStatus.SC_UNAUTHORIZED);
                log.info("user token has expired");
                return false;
            }
        }

        // check user state
        if (user.getState() == Flag.NO.ordinal()) {
            response.setStatus(HttpStatus.SC_UNAUTHORIZED);
            log.info(Status.USER_DISABLED.getMsg());
            return false;
        }
        request.setAttribute(Constants.SESSION_USER, user);
        ThreadLocalContext.setTimezone(user.getTimeZone());
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request,
                           HttpServletResponse response,
                           Object handler,
                           ModelAndView modelAndView) {

        int code = response.getStatus();
        if (code >= 200 && code < 300) {
            ApiServerMetrics.incApiResponse2xxCount();
        } else if (code >= 300 && code < 400) {
            ApiServerMetrics.incApiResponse3xxCount();
        } else if (code >= 400 && code < 500) {
            ApiServerMetrics.incApiResponse4xxCount();
        } else if (code >= 500 && code < 600) {
            ApiServerMetrics.incApiResponse5xxCount();
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        ThreadLocalContext.removeTimezone();
    }
}
