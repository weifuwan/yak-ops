package io.yak.ops.boot.controller.datasource.v1.converter;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.domain.DataSourceSummary;
import io.yak.ops.business.datasource.query.DataSourcePluginReader;
import io.yak.ops.common.PageData;
import io.yak.ops.common.PagingData;
import io.yak.ops.common.bean.vo.datasource.DataSourceOptionVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceSummaryVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceVO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataSourceViewConverter {
    private final DataSourcePluginReader pluginReader;

    public DataSourceVO definition(DataSourceDefinition source, boolean includeOriginalJson) {
        if (source == null) return null;
        DataSourceVO target = new DataSourceVO();
        target.setId(source.getId());
        target.setName(source.getName());
        target.setDbType(source.getDbType() == null ? null : source.getDbType().name());
        target.setJdbcUrl(pluginReader.maskSensitiveText(source.getJdbcUrl()));
        target.setEnvironment(
                source.getEnvironment() == null ? null : source.getEnvironment().name());
        target.setEnvironmentName(
                source.getEnvironment() == null ? null : source.getEnvironment().getDisplayName());
        target.setConnStatus(
                source.getConnStatus() == null ? null : source.getConnStatus().name());
        target.setRemark(source.getRemark());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
        if (includeOriginalJson && source.getDbType() != null) {
            target.setOriginalJson(pluginReader.maskConnectionJson(source.getDbType(), source.getOriginalJson()));
        }
        return target;
    }

    public PagingData<DataSourceVO> page(PageData<DataSourceDefinition> page) {
        return PagingData.from(page.map(value -> definition(value, false)));
    }

    public PagingData<DataSourceVO> all(List<DataSourceDefinition> definitions) {
        List<DataSourceVO> records = definitions == null
                ? List.of()
                : definitions.stream().map(value -> definition(value, false)).toList();
        long pages = records.isEmpty() ? 0L : 1L;
        long pageSize = Math.max(1, records.size());
        return PagingData.from(new PageData<>(records, records.size(), pages, 1L, pageSize));
    }

    public List<DataSourceOptionVO> options(List<DataSourceDefinition> definitions) {
        return definitions == null
                ? List.of()
                : definitions.stream().map(this::option).toList();
    }

    public DataSourceOptionVO option(DataSourceDefinition source) {
        return new DataSourceOptionVO(
                source.getName(),
                String.valueOf(source.getId()),
                source.getDbType() == null ? null : source.getDbType().name());
    }

    public DataSourceSummaryVO summary(DataSourceSummary source) {
        DataSourceSummary value = source == null ? DataSourceSummary.empty() : source;
        return new DataSourceSummaryVO(
                value.total(), value.connected(), value.disconnected(), value.unknown(), value.environmentCount());
    }
}
