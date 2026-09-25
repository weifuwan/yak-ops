package io.yak.ops.dao.repository.datasource;

import io.yak.ops.common.page.PageData;
import io.yak.ops.dao.entity.datasource.DataSourceEntity;
import io.yak.ops.dao.repository.BaseRepository;

/**
 * 定义数据源实体的持久化能力，隔离 Service Layer 与 MyBatis 细节。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
public interface DataSourceEntityRepository extends BaseRepository<DataSourceEntity> {

    /** 按数据源筛选条件查询分页数据。 */
    PageData<DataSourceEntity> queryPage(DataSourcePageQuery query);

    /** 判断数据源名称是否已被占用，可排除当前数据源 ID。 */
    boolean existsByName(String name, String excludeId);
}
