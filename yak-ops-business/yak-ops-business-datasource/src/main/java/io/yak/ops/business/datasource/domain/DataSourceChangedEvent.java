package io.yak.ops.business.datasource.domain;

/** Published after a persisted datasource configuration is changed or removed. */
public record DataSourceChangedEvent(Long dataSourceId) {

    public DataSourceChangedEvent {
        if (dataSourceId == null || dataSourceId <= 0L) {
            throw new IllegalArgumentException("dataSourceId must be positive");
        }
    }
}
