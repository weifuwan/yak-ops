package io.yak.ops.business.datasource.dao;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.ops.business.datasource.dao.model.DataSourceSummaryRow;
import io.yak.ops.common.bean.po.datasource.DataSourcePO;
import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.datasource.DataSourceEnvironment;
import java.util.List;

/** 数据源数据访问接口。 */
public interface DataSourceDao {

  int addDataSource(DataSourcePO dataSourcePO);

  int editDataSource(DataSourcePO dataSourcePO);

  DataSourcePO selectById(Long id);

  List<DataSourcePO> selectByIds(List<Long> ids);

  IPage<DataSourcePO> selectPage(PageQuery query);

  DataSourceSummaryRow selectSummary();

  List<DataSourcePO> selectAll(DataSourceDbType dbType);

  boolean existsByName(String name, Long excludeId);

  boolean deleteById(Long id);

  boolean updateConnectionStatus(Long id, DataSourceConnStatus connStatus);

  record PageQuery(
      int pageNo,
      int pageSize,
      String name,
      String keyword,
      DataSourceDbType dbType,
      DataSourceEnvironment environment,
      DataSourceConnStatus connStatus) {}
}
