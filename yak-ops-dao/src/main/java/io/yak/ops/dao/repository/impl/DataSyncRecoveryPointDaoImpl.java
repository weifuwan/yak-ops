package io.yak.ops.dao.repository.impl;

import io.yak.ops.dao.entity.DataSyncRecoveryPoint;
import io.yak.ops.dao.mapper.DataSyncRecoveryPointMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.DataSyncRecoveryPointDao;

import org.springframework.stereotype.Repository;

import lombok.NonNull;

@Repository
public class DataSyncRecoveryPointDaoImpl
        extends BaseDao<DataSyncRecoveryPoint, DataSyncRecoveryPointMapper>
        implements DataSyncRecoveryPointDao {

    public DataSyncRecoveryPointDaoImpl(@NonNull DataSyncRecoveryPointMapper mapper) {
        super(mapper);
    }
}
