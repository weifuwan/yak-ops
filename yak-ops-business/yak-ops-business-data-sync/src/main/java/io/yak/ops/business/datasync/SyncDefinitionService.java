package io.yak.ops.business.datasync;

import io.yak.ops.common.bean.dto.datasync.DataSyncTaskDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncTaskQueryDTO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTaskVO;
import io.yak.ops.common.page.PagingData;

/**
 * SyncDefinitionService 业务接口。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
public interface SyncDefinitionService {

    DataSyncTaskVO createTask(DataSyncTaskDTO dto);

    DataSyncTaskVO createTask(DataSyncTaskDTO dto, String operatorUserId);

    DataSyncTaskVO updateTask(String id, DataSyncTaskDTO dto);

    DataSyncTaskVO updateTask(String id, DataSyncTaskDTO dto, String operatorUserId);

    DataSyncTaskVO queryTask(String id);

    PagingData<DataSyncTaskVO> queryTaskPage(DataSyncTaskQueryDTO dto);

    DataSyncTaskVO publishTask(String id);

    DataSyncTaskVO publishTask(String id, String operatorUserId);

    DataSyncTaskVO unpublishTask(String id);

    DataSyncTaskVO unpublishTask(String id, String operatorUserId);

    boolean deleteTask(String id);
}
