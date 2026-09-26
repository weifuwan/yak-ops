package io.yak.ops.common.bean.vo.datasource;

import lombok.Data;

/**
 * 单个数据源的批量连接测试结果。
 *
 * @author weifuwan
 * @since 2026-09-26
 */
@Data
public class DataSourceBatchConnectTestResultVO {

    /** 数据源 ID。 */
    private String dataSourceId;

    /** 是否连接成功。 */
    private boolean connected;
}
