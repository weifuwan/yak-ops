package io.yak.ops.dao.repository;

import io.yak.ops.dao.entity.YakOpsVersion;

import java.util.Optional;

public interface YakOpsVersionDao extends IDao<YakOpsVersion> {

    Optional<YakOpsVersion> selectVersion();

}
