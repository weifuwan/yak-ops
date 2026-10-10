package io.yak.ops.business.datasync;

import io.yak.ops.common.bean.dto.datasync.DataSyncOperationsDashboardDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncTaskQueryDTO;
import io.yak.ops.common.bean.vo.datasync.DataSyncOperationsDashboardVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTaskOperationVO;
import io.yak.ops.common.page.PagingData;

/**
 * DataSyncOperationsService 业务接口。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
public interface DataSyncOperationsService {

    PagingData<DataSyncTaskOperationVO> queryTaskOperationPage(DataSyncTaskQueryDTO dto);

    DataSyncOperationsDashboardVO queryOperationsDashboard(DataSyncOperationsDashboardDTO dto);
}
