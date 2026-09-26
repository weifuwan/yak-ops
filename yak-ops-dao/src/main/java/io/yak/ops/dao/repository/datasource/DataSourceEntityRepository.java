package io.yak.ops.dao.repository.datasource;

import io.yak.ops.common.page.PageData;
import io.yak.ops.dao.entity.datasource.DataSourceEntity;
import io.yak.ops.dao.repository.BaseRepository;
import java.util.Optional;

/**
 * 定义 Workspace-scoped 数据源持久化能力，隔离 Service Layer 与 MyBatis 细节。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
public interface DataSourceEntityRepository extends BaseRepository<DataSourceEntity> {

    /** 在指定 Workspace 内按数据源筛选条件查询分页数据。 */
    PageData<DataSourceEntity> queryPage(String workspaceId, DataSourcePageQuery query);

    /** 在指定 Workspace 内根据数据源 ID 查询。 */
    Optional<DataSourceEntity> queryById(String workspaceId, String id);

    /** 在指定 Workspace 内更新数据源。 */
    DataSourceEntity update(String workspaceId, DataSourceEntity entity);

    /** 在指定 Workspace 内删除数据源。 */
    int deleteById(String workspaceId, String id);

    /** 判断指定 Workspace 内的数据源名称是否已被占用，可排除当前数据源 ID。 */
    boolean existsByName(String workspaceId, String name, String excludeId);
}
