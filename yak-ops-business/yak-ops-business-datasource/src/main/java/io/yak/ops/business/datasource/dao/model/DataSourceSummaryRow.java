package io.yak.ops.business.datasource.dao.model;

import lombok.Data;

/** 数据源统计 SQL 的持久化投影，不作为 HTTP 响应模型。 */
@Data
public class DataSourceSummaryRow {
    private long total;
    private long connected;
    private long disconnected;
    private long unknown;
    private long environmentCount;
}
