package io.yak.ops.business.task.metrics;

import io.yak.ops.common.bean.vo.task.DataSyncOperationsVO;

/** Data Sync operations summary and current running-state read contract. */
public interface DataSyncOperationsService {

    /**
     * Returns Workspace-scoped metrics from pinned Task Instance definitions.
     *
     * @param workspaceId owning Workspace
     * @param syncType OFFLINE or REALTIME
     * @param range TODAY, LAST_7_DAYS or LAST_30_DAYS
     * @return period outcomes, current running count, trends and latest failures
     */
    DataSyncOperationsVO dashboard(String workspaceId, String syncType, String range);
}
