package io.yak.ops.api.controller;

import io.yak.ops.api.ApiApplicationServer;
import io.yak.ops.api.enums.Status;
import io.yak.ops.api.service.SessionService;
import io.yak.ops.api.service.UsersService;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.dao.DaoConfiguration;
import io.yak.ops.dao.entity.Session;
import io.yak.ops.dao.entity.User;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = {ApiApplicationServer.class, DaoConfiguration.class})
@AutoConfigureMockMvc
@Transactional
public abstract class AbstractControllerTest {

    public static final String SESSION_ID = "sessionId";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private UsersService usersService;

    protected User user;

    protected String sessionId;

    @BeforeEach
    public void setUp() {
        user = usersService.queryUser(1);
        createSession(user);
    }

    private void createSession(User loginUser) {

        user = loginUser;

        Session session = sessionService.createSessionIfAbsent(loginUser);
        Assertions.assertNotNull(session);

        sessionId = session.getId();
        Assertions.assertNotNull(sessionId);
    }

    public Map<String, Object> success() {
        Map<String, Object> serviceResult = new HashMap<>();
        putMsg(serviceResult, Status.SUCCESS);
        return serviceResult;
    }

    public void putMsg(Map<String, Object> result, Status status, Object... statusParams) {
        result.put(Constants.STATUS, status);
        if (statusParams != null && statusParams.length > 0) {
            result.put(Constants.MSG, MessageFormat.format(status.getMsg(), statusParams));
        } else {
            result.put(Constants.MSG, status.getMsg());
        }
    }

    public void putMsg(Result<Object> result, Status status, Object... statusParams) {
        result.setCode(status.getCode());
        if (statusParams != null && statusParams.length > 0) {
            result.setMsg(MessageFormat.format(status.getMsg(), statusParams));
        } else {
            result.setMsg(status.getMsg());
        }
    }

}
