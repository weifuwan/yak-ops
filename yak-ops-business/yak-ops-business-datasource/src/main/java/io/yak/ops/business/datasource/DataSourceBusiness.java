package io.yak.ops.business.datasource;

import io.yak.ops.common.PagingData;
import io.yak.ops.common.bean.dto.datasource.DataSourceConnectTestDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceQueryDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceOptionVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceSummaryVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceVO;
import java.util.List;

/**
 * 提供数据源配置管理、查询和连接测试等核心业务能力，是 Boot 访问 Datasource 管理能力的唯一入口。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
public interface DataSourceBusiness {

    boolean addDataSource(DataSourceDTO dto);

    boolean updateDataSource(String id, DataSourceDTO dto);

    DataSourceVO queryDataSource(String id);

    boolean deleteDataSource(String id);

    PagingData<DataSourceVO> queryDataSourcePage(DataSourceQueryDTO dto);

    DataSourceSummaryVO queryDataSourceSummary();

    PagingData<DataSourceVO> queryAllDataSources();

    List<DataSourceOptionVO> queryDataSourceOptions(String dbType);

    boolean testConnection(String id);

    boolean testConnection(DataSourceConnectTestDTO dto);
}
