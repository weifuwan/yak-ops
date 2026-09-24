package io.yak.ops.business.datasource.dao.impl;

import jakarta.annotation.Resource;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.dao.DataSourceDao;
import io.yak.ops.business.datasource.dao.mapper.DataSourceMapper;
import io.yak.ops.business.datasource.dao.model.DataSourceSummaryRow;
import io.yak.ops.common.bean.po.datasource.DataSourcePO;
import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** MyBatis DataSource DAO. */
@Repository
@ConditionalOnDataSourceEnabled
public class DataSourceDaoImpl implements DataSourceDao {

    @Resource
    private DataSourceMapper dataSourceMapper;

    @Override
    public int addDataSource(DataSourcePO dataSourcePO) {
        return dataSourceMapper.insert(dataSourcePO);
    }

    @Override
    public int editDataSource(DataSourcePO dataSourcePO) {
        return dataSourceMapper.updateById(dataSourcePO);
    }

    @Override
    public DataSourcePO selectById(Long id) {
        return id == null ? null : dataSourceMapper.selectById(id);
    }

    @Override
    public List<DataSourcePO> selectByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<Long> normalizedIds =
                ids.stream().filter(Objects::nonNull).distinct().toList();
        return normalizedIds.isEmpty() ? List.of() : dataSourceMapper.selectBatchIds(normalizedIds);
    }

    @Override
    public IPage<DataSourcePO> selectPage(PageQuery query) {
        PageQuery condition = query == null ? new PageQuery(1, 10, null, null, null, null, null) : query;
        Page<DataSourcePO> page = Page.of(Math.max(1, condition.pageNo()), Math.max(1, condition.pageSize()));
        return dataSourceMapper.selectPage(
                page,
                queryWrapper(condition).orderByDesc(DataSourcePO::getUpdateTime).orderByDesc(DataSourcePO::getId));
    }

    @Override
    public DataSourceSummaryRow selectSummary() {
        return dataSourceMapper.selectSummary();
    }

    @Override
    public List<DataSourcePO> selectAll(DataSourceDbType dbType) {
        return dataSourceMapper.selectList(Wrappers.<DataSourcePO>lambdaQuery()
                .eq(dbType != null, DataSourcePO::getDbType, dbType)
                .orderByAsc(DataSourcePO::getName)
                .orderByAsc(DataSourcePO::getId));
    }

    @Override
    public boolean existsByName(String name, Long excludeId) {
        if (!StringUtils.hasText(name)) return false;
        Long count = dataSourceMapper.selectCount(Wrappers.<DataSourcePO>lambdaQuery()
                .eq(DataSourcePO::getName, name)
                .ne(excludeId != null, DataSourcePO::getId, excludeId));
        return count != null && count > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return id != null && dataSourceMapper.deleteById(id) > 0;
    }

    @Override
    public boolean updateConnectionStatus(Long id, DataSourceConnStatus connStatus) {
        if (id == null || connStatus == null) return false;
        return dataSourceMapper.update(
                        null,
                        Wrappers.<DataSourcePO>lambdaUpdate()
                                .set(DataSourcePO::getConnStatus, connStatus)
                                .eq(DataSourcePO::getId, id))
                > 0;
    }

    private LambdaQueryWrapper<DataSourcePO> queryWrapper(PageQuery query) {
        LambdaQueryWrapper<DataSourcePO> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.hasText(query.keyword())) {
            wrapper.and(nested -> nested.like(DataSourcePO::getName, query.keyword())
                    .or()
                    .like(DataSourcePO::getJdbcUrl, query.keyword()));
        }
        return wrapper.like(StringUtils.hasText(query.name()), DataSourcePO::getName, query.name())
                .eq(query.dbType() != null, DataSourcePO::getDbType, query.dbType())
                .eq(query.environment() != null, DataSourcePO::getEnvironment, query.environment())
                .eq(query.connStatus() != null, DataSourcePO::getConnStatus, query.connStatus());
    }
}
