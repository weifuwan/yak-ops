package io.yak.ops.dao.repository.impl;

import io.yak.ops.dao.entity.DatasourceUser;
import io.yak.ops.dao.mapper.DataSourceUserMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.DataSourceUserDao;

import org.springframework.stereotype.Repository;

import lombok.NonNull;

@Repository
public class DataSourceUserDaoImpl extends BaseDao<DatasourceUser, DataSourceUserMapper> implements DataSourceUserDao {

    public DataSourceUserDaoImpl(@NonNull DataSourceUserMapper dataSourceUserMapper) {
        super(dataSourceUserMapper);
    }

    @Override
    public void deleteByUserId(int userId) {
        mybatisMapper.deleteByUserId(userId);
    }

    @Override
    public void deleteByDatasourceId(int datasourceId) {
        mybatisMapper.deleteByDatasourceId(datasourceId);
    }
}
