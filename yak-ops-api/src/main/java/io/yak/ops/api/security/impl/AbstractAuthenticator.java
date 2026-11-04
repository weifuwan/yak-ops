/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.yak.ops.api.security.impl;

import io.yak.ops.api.enums.Status;
import io.yak.ops.api.security.AuthenticationType;
import io.yak.ops.api.security.Authenticator;
import io.yak.ops.api.security.SecurityConfig;
import io.yak.ops.api.service.SessionService;
import io.yak.ops.api.service.UsersService;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.common.enums.Flag;
import io.yak.ops.dao.entity.Session;
import io.yak.ops.dao.entity.User;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.util.WebUtils;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractAuthenticator implements Authenticator {

    @Autowired
    protected UsersService userService;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private SecurityConfig securityConfig;

    /**
     * user login and return user in db
     *
     * @param userName user identity field
     * @param password user login password
     * @return user object in databse
     */
    public abstract User login(@NonNull String userName, String password);

    @Override
    public Result<Map<String, String>> authenticate(@NonNull String userName, String password, @NonNull String ip) {
        Result<Map<String, String>> result = new Result<>();
        User user = login(userName, password);
        if (user == null) {
            if (Objects.equals(securityConfig.getType(), AuthenticationType.CASDOOR_SSO.name())) {
                log.error("State or code entered incorrectly.");
                result.setCode(Status.STATE_CODE_ERROR.getCode());
                result.setMsg(Status.STATE_CODE_ERROR.getMsg());
            } else {
                log.error("Username or password entered incorrectly.");
                result.setCode(Status.USER_NAME_PASSWD_ERROR.getCode());
                result.setMsg(Status.USER_NAME_PASSWD_ERROR.getMsg());
            }
            return result;
        }

        // check user state
        if (user.getState() == Flag.NO.ordinal()) {
            log.error("The current user is deactivated, userName:{}.", user.getUserName());
            result.setCode(Status.USER_DISABLED.getCode());
            result.setMsg(Status.USER_DISABLED.getMsg());
            return result;
        }

        // create session
        Session session = sessionService.createSessionIfAbsent(user);
        if (session == null) {
            result.setCode(Status.LOGIN_SESSION_FAILED.getCode());
            result.setMsg(Status.LOGIN_SESSION_FAILED.getMsg());
            return result;
        }

        log.info("Session is created, userName:{}.", user.getUserName());

        Map<String, String> data = new HashMap<>();
        data.put(Constants.SESSION_ID, session.getId());
        data.put(Constants.SECURITY_CONFIG_TYPE, securityConfig.getType());

        result.setData(data);
        result.setCode(Status.SUCCESS.getCode());
        result.setMsg(Status.LOGIN_SUCCESS.getMsg());
        return result;
    }

    @Override
    public User getAuthUser(HttpServletRequest request) {
        String sessionId = request.getHeader(Constants.SESSION_ID);
        if (StringUtils.isBlank(sessionId)) {
            Cookie cookie = WebUtils.getCookie(request, Constants.SESSION_ID);
            if (cookie != null) {
                sessionId = cookie.getValue();
            }
        }
        final Session session = sessionService.getSession(sessionId);
        if (session == null) {
            return null;
        }
        if (sessionService.isSessionExpire(session)) {
            sessionService.expireSession(session.getUserId());
            return null;
        }
        // get user object from session
        return userService.queryUser(session.getUserId());
    }

}
