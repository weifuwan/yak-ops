package io.yak.ops.dao.repository.datasource;

import io.yak.ops.common.PageData;
import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.datasource.DataSourceEnvironment;
import io.yak.ops.dao.entity.datasource.DataSourceEntity;
import io.yak.ops.dao.model.datasource.DataSourceSummaryRow;
import io.yak.ops.dao.repository.BaseRepository;
import java.util.List;

/**
 * 定义数据源实体的持久化能力，隔离 Business 与 MyBatis 细节。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
public interface DataSourceEntityRepository extends BaseRepository<DataSourceEntity> {

    /** 按数据源筛选条件查询分页数据。 */
    PageData<DataSourceEntity> queryPage(PageQuery query);

    /** 按数据库类型查询全部数据源并保持稳定排序。 */
    List<DataSourceEntity> queryAll(DataSourceDbType dbType);

    /** 查询数据源统计聚合结果。 */
    DataSourceSummaryRow querySummary();

    /** 判断数据源名称是否已被占用，可排除当前数据源 ID。 */
    boolean existsByName(String name, String excludeId);

    /**
     * 数据源分页筛选条件。
     *
     * @param pageNo 当前页码，从 1 开始
     * @param pageSize 每页数量
     * @param name 数据源名称模糊匹配条件
     * @param keyword 名称或 JDBC 地址关键字
     * @param dbType 数据库类型
     * @param environment 运行环境
     * @param connStatus 连通状态
     * @author weifuwan
     * @since 2026-09-24
     */
    record PageQuery(
            int pageNo,
            int pageSize,
            String name,
            String keyword,
            DataSourceDbType dbType,
            DataSourceEnvironment environment,
            DataSourceConnStatus connStatus) {}
}
