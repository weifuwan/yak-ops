package io.yak.ops.business.datasource.domain;

/** 数据源总览领域统计。 */
public record DataSourceSummary(long total, long connected, long disconnected, long unknown, long environmentCount) {

    public static DataSourceSummary empty() {
        return new DataSourceSummary(0L, 0L, 0L, 0L, 0L);
    }
}
