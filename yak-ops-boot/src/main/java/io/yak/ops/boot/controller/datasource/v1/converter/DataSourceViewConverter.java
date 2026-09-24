package io.yak.ops.boot.controller.datasource.v1.converter;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.plugin.DataSourcePluginRegistry;
import io.yak.ops.common.PageData;
import io.yak.ops.common.PagingData;
import io.yak.ops.common.bean.vo.datasource.DataSourceOptionVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceSummaryVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceVO;
import io.yak.ops.dao.entity.datasource.DataSourceEntity;
import io.yak.ops.dao.model.datasource.DataSourceSummaryRow;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 将 DAO 数据源实体转换为 HTTP 展示对象，并统一遮罩连接敏感信息。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Component
@ConditionalOnDataSourceEnabled
public class DataSourceViewConverter {

    @Resource
    private DataSourcePluginRegistry pluginRegistry;

    public DataSourceVO entity(DataSourceEntity source, boolean includeOriginalJson) {
        if (source == null) return null;
        DataSourceVO target = new DataSourceVO();
        target.setId(source.getId());
        target.setName(source.getName());
        target.setDbType(source.getDbType() == null ? null : source.getDbType().name());
        target.setJdbcUrl(pluginRegistry.maskSensitiveText(source.getJdbcUrl()));
        target.setEnvironment(source.getEnvironment() == null ? null : source.getEnvironment().name());
        target.setEnvironmentName(
                source.getEnvironment() == null ? null : source.getEnvironment().getDisplayName());
        target.setConnStatus(source.getConnStatus() == null ? null : source.getConnStatus().name());
        target.setRemark(source.getRemark());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
        if (includeOriginalJson && source.getDbType() != null) {
            target.setOriginalJson(pluginRegistry.maskConnectionJson(source.getDbType(), source.getOriginalJson()));
        }
        return target;
    }

    public PagingData<DataSourceVO> page(PageData<DataSourceEntity> page) {
        return PagingData.from(page.map(value -> entity(value, false)));
    }

    public PagingData<DataSourceVO> all(List<DataSourceEntity> entities) {
        List<DataSourceVO> records =
                entities == null ? List.of() : entities.stream().map(value -> entity(value, false)).toList();
        long pages = records.isEmpty() ? 0L : 1L;
        long pageSize = Math.max(1, records.size());
        return PagingData.from(new PageData<>(records, records.size(), pages, 1L, pageSize));
    }

    public List<DataSourceOptionVO> options(List<DataSourceEntity> entities) {
        return entities == null ? List.of() : entities.stream().map(this::option).toList();
    }

    public DataSourceOptionVO option(DataSourceEntity source) {
        return new DataSourceOptionVO(
                source.getName(),
                String.valueOf(source.getId()),
                source.getDbType() == null ? null : source.getDbType().name());
    }

    public DataSourceSummaryVO summary(DataSourceSummaryRow source) {
        if (source == null) return new DataSourceSummaryVO(0L, 0L, 0L, 0L, 0L);
        return new DataSourceSummaryVO(
                source.getTotal(),
                source.getConnected(),
                source.getDisconnected(),
                source.getUnknown(),
                source.getEnvironmentCount());
    }
}
