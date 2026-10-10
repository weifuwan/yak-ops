package io.yak.ops.business.datasync;

import io.yak.ops.common.bean.dto.datasync.DataSyncScheduleDTO;
import io.yak.ops.common.bean.vo.datasync.DataSyncSchedulePreviewVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncScheduleVO;

/**
 * DataSyncScheduleService 业务接口。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
public interface DataSyncScheduleService {

    DataSyncScheduleVO saveSchedule(String taskId, DataSyncScheduleDTO dto);

    DataSyncSchedulePreviewVO previewSchedule(DataSyncScheduleDTO dto);

    DataSyncScheduleVO querySchedule(String taskId);

    DataSyncScheduleVO enableSchedule(String taskId);

    DataSyncScheduleVO disableSchedule(String taskId);

    void restoreScheduleRuntime();
    /** 任务下线时停用调度，和任务状态更新处于同一事务。 */
    void disableForTask(String workspaceId, String taskId);

    /** 任务删除时清理调度定义。 */
    void deleteForTask(String workspaceId, String taskId);
}
