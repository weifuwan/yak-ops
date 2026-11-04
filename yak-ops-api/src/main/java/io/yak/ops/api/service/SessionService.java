package io.yak.ops.api.service;

import io.yak.ops.dao.entity.Session;
import io.yak.ops.dao.entity.User;

public interface SessionService {

    Session getSession(String sessionId);

    Session createSessionIfAbsent(User user);

    void expireSession(Integer userId);

    boolean isSessionExpire(Session session);
}
