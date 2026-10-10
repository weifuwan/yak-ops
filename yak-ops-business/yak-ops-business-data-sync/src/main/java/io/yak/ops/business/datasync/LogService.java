package io.yak.ops.business.datasync;

import io.yak.ops.common.bean.vo.datasync.DataSyncExecutionEventVO;
import java.util.List;

/**
 * DATA_SYNC 历史实例的结构化产品事件入口，不充当YakFlow运行日志文件读取器。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
public interface LogService {

    List<DataSyncExecutionEventVO> queryExecutionEvents(String instanceId);
}
