package io.yak.ops.dao.repository.datasync;

import lombok.Data;

/**
 * Data Sync Operations Dashboard Execution 状态分布持久化读模型。
 *
 * @author weifuwan
 * @since 2026-09-30
 */
@Data
public class DataSyncOperationsStatusStats {

    private Integer status;

    private Long count;
}
