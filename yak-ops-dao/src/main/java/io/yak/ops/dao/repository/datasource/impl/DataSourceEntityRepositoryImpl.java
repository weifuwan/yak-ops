package io.yak.ops.dao.repository.datasource.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.common.page.PageData;
import io.yak.ops.dao.entity.datasource.DataSourceEntity;
import io.yak.ops.dao.mapper.datasource.DataSourceMapper;
import io.yak.ops.dao.model.datasource.DataSourceSummaryRow;
import io.yak.ops.dao.repository.datasource.DataSourceEntityRepository;
import io.yak.ops.dao.repository.impl.BaseRepositoryImpl;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * 使用 MyBatis-Plus 实现数据源实体查询、统计和状态更新。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Repository
public class DataSourceEntityRepositoryImpl
        extends BaseRepositoryImpl<DataSourceMapper, DataSourceEntity>
        implements DataSourceEntityRepository {

    @Resource
    private DataSourceMapper dataSourceMapper;

    @Override
    protected DataSourceMapper mapper() {
        return dataSourceMapper;
    }

    @Override
    public PageData<DataSourceEntity> queryPage(PageQuery query) {
        PageQuery condition = query == null ? new PageQuery(1, 10, null, null, null, null, null) : query;
        Page<DataSourceEntity> page =
                Page.of(Math.max(1, condition.pageNo()), Math.max(1, condition.pageSize()));
        IPage<DataSourceEntity> result = dataSourceMapper.selectPage(
                page,
                queryWrapper(condition)
                        .orderByDesc(DataSourceEntity::getUpdateTime)
                        .orderByDesc(DataSourceEntity::getId));
        return new PageData<>(
                result.getRecords(), result.getTotal(), result.getPages(), result.getCurrent(), result.getSize());
    }

    @Override
    public List<DataSourceEntity> queryAll(String dbType) {
        return dataSourceMapper.selectList(Wrappers.<DataSourceEntity>lambdaQuery()
                .eq(dbType != null, DataSourceEntity::getDbType, dbType)
                .orderByAsc(DataSourceEntity::getName)
                .orderByAsc(DataSourceEntity::getId));
    }

    @Override
    public DataSourceSummaryRow querySummary() {
        return dataSourceMapper.selectSummary();
    }

    @Override
    public boolean existsByName(String name, String excludeId) {
        if (!StringUtils.hasText(name)) return false;
        Long count = dataSourceMapper.selectCount(Wrappers.<DataSourceEntity>lambdaQuery()
                .eq(DataSourceEntity::getName, name)
                .ne(excludeId != null, DataSourceEntity::getId, excludeId));
        return count != null && count > 0;
    }

    private LambdaQueryWrapper<DataSourceEntity> queryWrapper(PageQuery query) {
        LambdaQueryWrapper<DataSourceEntity> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.hasText(query.keyword())) {
            wrapper.and(nested -> nested.like(DataSourceEntity::getName, query.keyword())
                    .or()
                    .like(DataSourceEntity::getJdbcUrl, query.keyword()));
        }
        return wrapper.like(StringUtils.hasText(query.name()), DataSourceEntity::getName, query.name())
                .eq(query.dbType() != null, DataSourceEntity::getDbType, query.dbType())
                .eq(query.environment() != null, DataSourceEntity::getEnvironment, query.environment())
                .eq(query.connStatus() != null, DataSourceEntity::getConnStatus, query.connStatus());
    }
}
