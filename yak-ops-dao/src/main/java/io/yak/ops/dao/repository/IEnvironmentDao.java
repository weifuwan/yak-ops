package io.yak.ops.dao.repository;

import io.yak.ops.dao.entity.Environment;

import java.util.Optional;

public interface IEnvironmentDao extends IDao<Environment> {

    Optional<Environment> queryByEnvironmentCode(Long environmentCode);
}
