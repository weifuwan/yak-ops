package io.yak.ops.business.datasync;

import io.yak.ops.common.bean.dto.datasync.DataSyncInstanceQueryDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncOperationsDashboardDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncScheduleDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncTaskDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncTaskQueryDTO;
import io.yak.ops.common.bean.vo.datasync.DataSyncAttemptVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncExecutionEventVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncInstanceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncOperationsDashboardVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncSchedulePreviewVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncScheduleVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncSinkTraceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncSourceTraceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTableAttemptVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTaskOperationVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTaskVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTracePageVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTraceSummaryVO;
import io.yak.ops.common.page.PagingData;
import java.util.List;

/**
 * Data Sync 对上层暴露的唯一稳定 Service Contract。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
public interface DataSyncService {

    DataSyncTaskVO createTask(DataSyncTaskDTO dto);

    DataSyncTaskVO createTask(DataSyncTaskDTO dto, String operatorUserId);

    DataSyncTaskVO updateTask(String id, DataSyncTaskDTO dto);

    DataSyncTaskVO updateTask(String id, DataSyncTaskDTO dto, String operatorUserId);

    DataSyncTaskVO queryTask(String id);

    PagingData<DataSyncTaskVO> queryTaskPage(DataSyncTaskQueryDTO dto);

    PagingData<DataSyncTaskOperationVO> queryTaskOperationPage(DataSyncTaskQueryDTO dto);

    DataSyncOperationsDashboardVO queryOperationsDashboard(DataSyncOperationsDashboardDTO dto);

    DataSyncTaskVO publishTask(String id);

    DataSyncTaskVO publishTask(String id, String operatorUserId);

    DataSyncTaskVO unpublishTask(String id);

    DataSyncTaskVO unpublishTask(String id, String operatorUserId);

    DataSyncInstanceVO runTask(String id);

    DataSyncScheduleVO saveSchedule(String taskId, DataSyncScheduleDTO dto);

    DataSyncSchedulePreviewVO previewSchedule(DataSyncScheduleDTO dto);

    /** 查询离线同步任务调度；任务未配置调度时返回 {@code null}。 */
    DataSyncScheduleVO querySchedule(String taskId);

    DataSyncScheduleVO enableSchedule(String taskId);

    DataSyncScheduleVO disableSchedule(String taskId);

    void restoreScheduleRuntime();

    void restoreRealtimeDesiredState();

    boolean deleteTask(String id);

    DataSyncInstanceVO queryInstance(String id);

    List<DataSyncAttemptVO> queryAttempts(String instanceId);

    /** 按 Workspace 和 Root / Table 关联校验后读取一张表的 Attempt 历史。 */
    List<DataSyncTableAttemptVO> queryTableAttempts(String instanceId, String tableExecutionId);

    List<DataSyncExecutionEventVO> queryExecutionEvents(String instanceId);

    DataSyncTraceSummaryVO queryExecutionTraceSummary(String instanceId, Integer attemptNo);

    DataSyncTracePageVO<DataSyncSourceTraceVO> queryExecutionSourceTrace(
            String instanceId, Integer attemptNo, Integer pageSize, String cursor, String status);

    DataSyncTracePageVO<DataSyncSinkTraceVO> queryExecutionSinkTrace(
            String instanceId, Integer attemptNo, Integer pageSize, String cursor, String status);

    PagingData<DataSyncInstanceVO> queryInstancePage(DataSyncInstanceQueryDTO dto);

    DataSyncInstanceVO cancelInstance(String id);
}
