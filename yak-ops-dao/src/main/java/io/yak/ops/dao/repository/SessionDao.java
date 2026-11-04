package io.yak.ops.dao.repository;

import io.yak.ops.dao.entity.Session;

import java.util.List;

public interface SessionDao extends IDao<Session> {

    void deleteByUserId(Integer userId);

    List<Session> queryByUserId(Integer userId);

}
