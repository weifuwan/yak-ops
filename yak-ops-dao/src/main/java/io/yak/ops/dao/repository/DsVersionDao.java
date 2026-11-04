package io.yak.ops.dao.repository;

import io.yak.ops.dao.entity.DsVersion;

import java.util.Optional;

public interface DsVersionDao extends IDao<DsVersion> {

    Optional<DsVersion> selectVersion();

}
