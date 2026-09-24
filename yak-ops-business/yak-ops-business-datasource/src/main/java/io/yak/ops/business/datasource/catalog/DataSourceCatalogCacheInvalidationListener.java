package io.yak.ops.business.datasource.catalog;

import jakarta.annotation.Resource;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.domain.DataSourceChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Clears stale local Catalog metadata only after datasource mutations commit successfully. */
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
