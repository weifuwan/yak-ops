package io.yak.ops.dao.repository.impl;

import io.yak.ops.dao.entity.Environment;
import io.yak.ops.dao.mapper.EnvironmentMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.IEnvironmentDao;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import lombok.NonNull;

@Repository
public class EnvironmentDaoImpl extends BaseDao<Environment, EnvironmentMapper> implements IEnvironmentDao {

    public EnvironmentDaoImpl(@NonNull EnvironmentMapper environmentMapper) {
        super(environmentMapper);
    }

    @Override
    public Optional<Environment> queryByEnvironmentCode(Long environmentCode) {
        return Optional.ofNullable(mybatisMapper.queryByEnvironmentCode(environmentCode));
    }
}
