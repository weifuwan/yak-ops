package io.yak.ops.api.service.impl;

import io.yak.ops.api.service.SessionService;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.dao.entity.Session;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.repository.SessionDao;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

/**
 * session service implement
 */
@Service
@Slf4j
public class SessionServiceImpl extends BaseServiceImpl implements SessionService {

    @Autowired
    private SessionDao sessionDao;

    @Override
    public Session getSession(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return null;
        }
        return sessionDao.queryById(sessionId);
    }

    @Override
    @Transactional
    public Session createSessionIfAbsent(User user) {
        Session session;
        List<Session> sessionList = sessionDao.queryByUserId(user.getId());
        // todo: this can be remove after the old session data is cleared
        if (CollectionUtils.isNotEmpty(sessionList)) {
            // is session list greater 1 ， delete other ，get one
            if (sessionList.size() > 1) {
                for (int i = 1; i < sessionList.size(); i++) {
                    sessionDao.deleteById(sessionList.get(i).getId());
                }
            }
            session = sessionList.get(0);
            if (isSessionExpire(session)) {
                session.setLastLoginTime(new Date());
                sessionDao.updateById(session);
                return session;
            } else {
                sessionDao.deleteById(session.getId());
            }
        }

        // assign new session
        Session newSession = Session.builder()
                .id(UUID.randomUUID().toString())
                .userId(user.getId())
                .lastLoginTime(new Date())
                .build();
        sessionDao.insert(newSession);
        return newSession;
    }

    @Override
    public void expireSession(Integer userId) {
        sessionDao.deleteByUserId(userId);
    }

    @Override
    public boolean isSessionExpire(Session session) {
        return System.currentTimeMillis() - session.getLastLoginTime().getTime() >= Constants.SESSION_TIME_OUT * 1000;
    }

}
