package io.yak.ops.business.datasource.query;

import jakarta.annotation.Resource;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.domain.DataSourceQuery;
import io.yak.ops.business.datasource.domain.DataSourceSummary;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.business.datasource.repository.DataSourceRepository;
import io.yak.ops.common.PageData;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import java.util.List;
import org.springframework.stereotype.Component;

/** Datasource read-side role returning domain models rather than transport models. */
@Component
@ConditionalOnDataSourceEnabled
public class DataSourceReader {

    @Resource
    private DataSourceRepository repository;

    public DataSourceDefinition require(Long id) {
        long dataSourceId = requireId(id);
        return repository
                .findById(dataSourceId)
                .orElseThrow(() -> new DataSourceException(DataSourceErrorCode.NOT_FOUND));
    }

    public PageData<DataSourceDefinition> page(DataSourceQuery query) {
        return repository.page(query);
    }

    public DataSourceSummary summary() {
        return repository.summary();
    }

    public List<DataSourceDefinition> findAll(DataSourceDbType dbType) {
        return repository.findAll(dbType);
    }

    private long requireId(Long id) {
        if (id == null || id <= 0L) {
            throw new DataSourceException(DataSourceErrorCode.NOT_FOUND);
        }
        return id;
    }
}
