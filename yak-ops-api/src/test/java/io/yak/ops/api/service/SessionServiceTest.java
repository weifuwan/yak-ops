package io.yak.ops.api.service;

import io.yak.ops.api.service.impl.SessionServiceImpl;
import io.yak.ops.common.enums.UserType;
import io.yak.ops.common.utils.DateUtils;
import io.yak.ops.dao.entity.Session;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.repository.SessionDao;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SessionServiceTest {

    @InjectMocks
    private SessionServiceImpl sessionService;

    @Mock
    private SessionDao sessionDao;

    private String sessionId = "aaaaaaaaaaaaaaaaaa";

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void after() {
    }

    @Test
    public void testGetSession() {

        Mockito.when(sessionDao.queryById(sessionId)).thenReturn(getSession());
        // query
        Session session = sessionService.getSession(sessionId);
        Assertions.assertNotNull(session);

    }

    /**
     * create session
     */
    @Test
    public void testCreateSession() {
        String ip = "127.0.0.1";
        User user = new User();
        user.setUserType(UserType.GENERAL_USER);
        user.setId(1);
        Mockito.when(sessionDao.queryByUserId(1)).thenReturn(getSessions());
        Session session = sessionService.createSessionIfAbsent(user);
        Assertions.assertNotNull(session);
        Assertions.assertNotNull(session.getId());
    }

    /**
     * sign out
     * remove ip restrictions
     */
    @Test
    public void testExpireSession() {
        int userId = 88888888;
        String ip = "127.0.0.1";
        User user = new User();
        user.setId(userId);

        Mockito.doNothing().when(sessionDao).deleteByUserId(userId);

        sessionService.expireSession(userId);

    }

    private Session getSession() {
        Session session = new Session();
        session.setId(sessionId);
        session.setIp("127.0.0.1");
        session.setLastLoginTime(DateUtils.add(new Date(), Calendar.DAY_OF_MONTH, 40));
        session.setUserId(1);
        return session;
    }

    private List<Session> getSessions() {
        List<Session> sessionList = new ArrayList<>();
        sessionList.add(getSession());
        return sessionList;
    }
}
