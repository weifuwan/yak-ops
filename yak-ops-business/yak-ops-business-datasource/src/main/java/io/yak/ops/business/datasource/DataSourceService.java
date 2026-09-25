package io.yak.ops.business.datasource;

import io.yak.ops.common.bean.dto.datasource.DataSourceConnectTestDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceQueryDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceVO;
import io.yak.ops.common.page.PagingData;

/**
 * 数据源管理唯一 Service 入口，负责增删改查、分页查询和连接测试。
 *
 * @author weifuwan
 * @since 2026-09-25
 */
public interface DataSourceService {

    boolean addDataSource(DataSourceDTO dto);

    boolean updateDataSource(String id, DataSourceDTO dto);

    DataSourceVO queryDataSource(String id);

    boolean deleteDataSource(String id);

    PagingData<DataSourceVO> queryDataSourcePage(DataSourceQueryDTO dto);

    boolean testConnection(String id);

    boolean testConnection(DataSourceConnectTestDTO dto);
}
