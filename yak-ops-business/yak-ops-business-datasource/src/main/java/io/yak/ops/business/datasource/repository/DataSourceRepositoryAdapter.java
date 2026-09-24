package io.yak.ops.business.datasource.repository;

import jakarta.annotation.Resource;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.dao.DataSourceDao;
import io.yak.ops.business.datasource.dao.DataSourceDao.PageQuery;
import io.yak.ops.business.datasource.dao.model.DataSourceSummaryRow;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.domain.DataSourceQuery;
import io.yak.ops.business.datasource.domain.DataSourceSummary;
import io.yak.ops.common.PageData;
import io.yak.ops.common.bean.po.datasource.DataSourcePO;
import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** MyBatis persistence adapter. */
@Repository
@ConditionalOnDataSourceEnabled
public class DataSourceRepositoryAdapter implements DataSourceRepository {

    @Resource
    private DataSourceDao dao;

    @Override
    public Optional<DataSourceDefinition> findById(Long id) {
        return Optional.ofNullable(toDomain(dao.selectById(id)));
    }

    @Override
    public boolean insert(DataSourceDefinition definition) {
        return dao.addDataSource(toPO(definition)) > 0;
    }

    @Override
    public boolean update(DataSourceDefinition definition) {
        return dao.editDataSource(toPO(definition)) > 0;
    }

    @Override
    public boolean delete(Long id) {
        return dao.deleteById(id);
    }

    @Override
    public boolean existsByName(String name, Long excludeId) {
        return dao.existsByName(name, excludeId);
    }

    @Override
    public PageData<DataSourceDefinition> page(DataSourceQuery query) {
        DataSourceQuery condition = query == null ? new DataSourceQuery(1, 10, null, null, null, null, null) : query;
        IPage<DataSourcePO> page = dao.selectPage(new PageQuery(
                condition.pageNo(),
                condition.pageSize(),
                condition.name(),
                condition.keyword(),
                condition.dbType(),
                condition.environment(),
                condition.connStatus()));
        List<DataSourceDefinition> records =
                page.getRecords().stream().map(this::toDomain).toList();
        return new PageData<>(records, page.getTotal(), page.getPages(), page.getCurrent(), page.getSize());
    }

    @Override
    public List<DataSourceDefinition> findAll(DataSourceDbType dbType) {
        return dao.selectAll(dbType).stream().map(this::toDomain).toList();
    }

    @Override
    public DataSourceSummary summary() {
        DataSourceSummaryRow row = dao.selectSummary();
        return row == null
                ? DataSourceSummary.empty()
                : new DataSourceSummary(
                        row.getTotal(),
                        row.getConnected(),
                        row.getDisconnected(),
                        row.getUnknown(),
                        row.getEnvironmentCount());
    }

    @Override
    public boolean updateConnectionStatus(Long id, DataSourceConnStatus status) {
        return dao.updateConnectionStatus(id, status);
    }

    private DataSourceDefinition toDomain(DataSourcePO po) {
        if (po == null) return null;
        return DataSourceDefinition.restore(
                po.getId(),
                po.getName(),
                po.getDbType(),
                po.getJdbcUrl(),
                po.getEnvironment(),
                po.getConnStatus(),
                po.getRemark(),
                po.getConnectionParams(),
                po.getOriginalJson(),
                po.getCreateTime(),
                po.getUpdateTime());
    }

    private DataSourcePO toPO(DataSourceDefinition definition) {
        DataSourcePO po = new DataSourcePO();
        po.setId(definition.getId());
        po.setName(definition.getName());
        po.setDbType(definition.getDbType());
        po.setJdbcUrl(definition.getJdbcUrl());
        po.setEnvironment(definition.getEnvironment());
        po.setConnStatus(definition.getConnStatus());
        po.setRemark(definition.getRemark());
        po.setConnectionParams(definition.getConnectionParams());
        po.setOriginalJson(definition.getOriginalJson());
        po.setCreateTime(definition.getCreateTime());
        po.setUpdateTime(definition.getUpdateTime());
        return po;
    }
}
