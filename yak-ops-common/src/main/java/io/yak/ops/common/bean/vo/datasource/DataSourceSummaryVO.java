package io.yak.ops.common.bean.vo.datasource;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 数据源管理总览统计。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataSourceSummaryVO {

    private long total;
    private long connected;
    private long disconnected;
    private long unknown;
    private long environmentCount;
}
