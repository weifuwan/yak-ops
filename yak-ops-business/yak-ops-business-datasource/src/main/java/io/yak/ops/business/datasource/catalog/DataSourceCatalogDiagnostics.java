package io.yak.ops.business.datasource.catalog;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.dao.entity.datasource.DataSourceEntity;
import jakarta.annotation.Resource;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 观测 Catalog 元数据访问耗时，并为超过阈值的慢调用记录必要上下文。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Slf4j
@Component
@ConditionalOnDataSourceEnabled
public class DataSourceCatalogDiagnostics {

    @Resource
    private DataSourceProperties properties;

    public <T> T observe(DataSourceEntity entity, String operation, Supplier<T> action) {
        long startedAt = System.nanoTime();
        boolean failed = false;
        try {
            return action.get();
        } catch (RuntimeException | Error exception) {
            failed = true;
            throw exception;
        } finally {
            logSlowOperation(entity, operation, Math.max(0L, System.nanoTime() - startedAt), failed);
        }
    }

    private void logSlowOperation(DataSourceEntity entity, String operation, long durationNanos, boolean failed) {
        long thresholdMs = Math.max(1L, properties.getCatalog().getSlowOperationThresholdMillis());
        long durationMs = TimeUnit.NANOSECONDS.toMillis(durationNanos);
        if (durationMs < thresholdMs) return;

        String operationName = operation == null || operation.isBlank() ? "unknown" : operation;
        String dbType = entity == null || entity.getDbType() == null ? "UNKNOWN" : entity.getDbType();
        String dataSourceId = entity == null ? null : entity.getId();
        log.warn(
                "Slow datasource catalog operation operation={} dataSourceId={} dbType={} durationMs={} thresholdMs={} failed={}",
                operationName,
                dataSourceId,
                dbType,
                durationMs,
                thresholdMs,
                failed);
    }
}
