package io.yak.ops.dao.repository;

import io.yak.ops.dao.entity.DatasourceUser;

public interface DataSourceUserDao extends IDao<DatasourceUser> {

    void deleteByUserId(int userId);

    void deleteByDatasourceId(int datasourceId);
}
