package io.yak.ops.business.datasource.catalog;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.management.DataSourceChangedEvent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 在数据源配置事务提交成功后失效对应的本地 Catalog 元数据缓存。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Slf4j
@Component
@ConditionalOnDataSourceEnabled
public class DataSourceCatalogCacheInvalidationListener {

    @Resource
    private DataSourceCatalogMetadataCache metadataCache;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDataSourceChanged(DataSourceChangedEvent event) {
        int invalidated = metadataCache.invalidate(event.dataSourceId());
        if (invalidated > 0) {
            log.debug(
                    "Invalidated datasource catalog cache dataSourceId={} entries={}",
                    event.dataSourceId(),
                    invalidated);
        }
    }
}
