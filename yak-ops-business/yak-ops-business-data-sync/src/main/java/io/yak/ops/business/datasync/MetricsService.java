package io.yak.ops.business.datasync;

import io.yak.ops.common.bean.dto.datasync.DataSyncOperationsDashboardDTO;
import io.yak.ops.common.bean.vo.datasync.DataSyncOperationsDashboardVO;

/**
 * DATA_SYNC 读写行数、状态趋势与异常排行等专属运维指标入口。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
public interface MetricsService {

    DataSyncOperationsDashboardVO queryOperationsDashboard(DataSyncOperationsDashboardDTO dto);
}
