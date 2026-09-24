package io.yak.ops.business.datasource.repository;

import io.yak.ops.common.PageData;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.domain.DataSourceQuery;
import io.yak.ops.business.datasource.domain.DataSourceSummary;
import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import java.util.List;
import java.util.Optional;

/** 数据源领域仓储。 */
public interface DataSourceRepository {
  Optional<DataSourceDefinition> findById(Long id);

  boolean insert(DataSourceDefinition definition);

  boolean update(DataSourceDefinition definition);

  boolean delete(Long id);

  boolean existsByName(String name, Long excludeId);

  PageData<DataSourceDefinition> page(DataSourceQuery query);

  List<DataSourceDefinition> findAll(DataSourceDbType dbType);

  DataSourceSummary summary();

  boolean updateConnectionStatus(Long id, DataSourceConnStatus status);
}
