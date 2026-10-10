package io.yak.ops.business.datasync;

import io.yak.ops.common.bean.dto.datasync.DataSyncInstanceQueryDTO;
import io.yak.ops.common.bean.vo.datasync.DataSyncAttemptVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncExecutionEventVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncInstanceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncSinkTraceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncSourceTraceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTableAttemptVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTracePageVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTraceSummaryVO;
import io.yak.ops.common.page.PagingData;
import java.util.List;

/**
 * DataSyncInstanceService 业务接口。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
public interface DataSyncInstanceService {

    DataSyncInstanceVO runTask(String id);

    DataSyncInstanceVO queryInstance(String id);

    List<DataSyncAttemptVO> queryAttempts(String instanceId);

    List<DataSyncTableAttemptVO> queryTableAttempts(String instanceId, String tableExecutionId);

    List<DataSyncExecutionEventVO> queryExecutionEvents(String instanceId);

    DataSyncTraceSummaryVO queryExecutionTraceSummary(String instanceId, Integer attemptNo);

    DataSyncTracePageVO<DataSyncSourceTraceVO> queryExecutionSourceTrace( String instanceId, Integer attemptNo, Integer pageSize, String cursor, String status);

    DataSyncTracePageVO<DataSyncSinkTraceVO> queryExecutionSinkTrace( String instanceId, Integer attemptNo, Integer pageSize, String cursor, String status);

    PagingData<DataSyncInstanceVO> queryInstancePage(DataSyncInstanceQueryDTO dto);

    DataSyncInstanceVO cancelInstance(String id);
}
