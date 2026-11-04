package io.yak.ops.dao.repository.impl;

import io.yak.ops.dao.entity.Session;
import io.yak.ops.dao.mapper.SessionMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.SessionDao;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import lombok.NonNull;

@Repository
public class SessionDaoImpl extends BaseDao<Session, SessionMapper> implements SessionDao {

    public SessionDaoImpl(@NonNull SessionMapper sessionMapper) {
        super(sessionMapper);
    }

    public void deleteByUserId(Integer userId) {
        mybatisMapper.delete(new QueryWrapper<>(Session.builder().userId(userId).build()));
    }

    @Override
    public List<Session> queryByUserId(Integer userId) {
        return mybatisMapper.selectList(new QueryWrapper<>(Session.builder().userId(userId).build()));
    }

}
