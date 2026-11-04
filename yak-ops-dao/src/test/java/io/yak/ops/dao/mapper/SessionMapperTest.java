package io.yak.ops.dao.mapper;

import io.yak.ops.dao.BaseDaoTest;
import io.yak.ops.dao.entity.Session;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

public class SessionMapperTest extends BaseDaoTest {

    @Autowired
    private SessionMapper sessionMapper;

    /**
     * insert
     *
     * @return Session
     */
    private Session insertOne() {
        // insertOne
        Session session = new Session();
        session.setId(UUID.randomUUID().toString());
        session.setLastLoginTime(new Date());
        session.setUserId(11111);
        sessionMapper.insert(session);
        return session;
    }

    /**
     * test update
     */
    @Test
    public void testUpdate() {
        // insertOne
        Session session = insertOne();
        session.setLastLoginTime(new Date());
        // update
        int update = sessionMapper.updateById(session);
        Assertions.assertEquals(1, update);
    }

    /**
     * test delete
     */
    @Test
    public void testDelete() {
        Session session = insertOne();
        int delete = sessionMapper.deleteById(session.getId());
        Assertions.assertEquals(1, delete);
    }

    /**
     * test query
     */
    @Test
    public void testQuery() {
        Session session = insertOne();
        // query
        List<Session> sessions = sessionMapper.selectList(null);
        Assertions.assertNotEquals(0, sessions.size());
    }

    /**
     * test query by userId
     */
    @Test
    public void testQueryByUserId() {
        Session session = insertOne();
        List<Session> sessions =
                sessionMapper.selectList(new QueryWrapper<>(Session.builder().userId(session.getUserId()).build()));
        Assertions.assertNotEquals(0, sessions.size());

    }
}
