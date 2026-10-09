package io.yak.ops.business.datasync.history;

import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.common.enums.datasync.DataSyncInstanceStatus;
import io.yak.ops.common.enums.datasync.DataSyncTableExecutionStatus;
import io.yak.ops.common.util.DateUtils;
import io.yak.ops.dao.entity.datasync.DataSyncInstanceEntity;
import io.yak.ops.dao.repository.datasync.DataSyncAttemptRepository;
import io.yak.ops.dao.repository.datasync.DataSyncInstanceRepository;
import io.yak.ops.dao.repository.datasync.DataSyncTableAttemptRepository;
import io.yak.ops.dao.repository.datasync.DataSyncTableExecutionRepository;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 旧运行时移除后的产品历史状态收口，不创建/恢复 YakFlow Task 或 Connector。
 *
 * <p>只有引擎尚未接入期间使用：上一进程遗留的 PENDING、RUNNING 和 RETRY_WAITING
 * 不可能继续执行，也不能假装 Retry 恢复成功。保留原有历史记录和快照。</p>
 */
@Component
@DependsOn("yakOpsFlyway")
public class DataSyncHistoryRecovery {

    private static final Logger LOG = LoggerFactory.getLogger(DataSyncHistoryRecovery.class);

    @Resource
    private DataSyncInstanceRepository instanceRepository;

    @Resource
    private DataSyncAttemptRepository attemptRepository;

    @Resource
    private DataSyncTableExecutionRepository tableExecutionRepository;

    @Resource
    private DataSyncTableAttemptRepository tableAttemptRepository;

    @Transactional(rollbackFor = Exception.class)
    public void closeAbandonedExecutions() {
        List<DataSyncInstanceEntity> active = instanceRepository.queryActive();
        if (active.isEmpty()) return;

        LocalDateTime finishedAt = DateUtils.now();
        int errorCode = DataSyncErrorCode.EXECUTION_LOST.getCode();
        String message = DataSyncErrorCode.EXECUTION_LOST.getMessage();

        int attempts = attemptRepository.markActiveAsLost(finishedAt, errorCode, message);
        int tableAttempts = tableAttemptRepository.markActiveAsLost(finishedAt, errorCode, message);
        int executions = instanceRepository.markActiveAsLost(finishedAt, errorCode, message);
        int retryWaiting = 0;
        for (DataSyncInstanceEntity execution : active) {
            if (execution.getStatus() == DataSyncInstanceStatus.RETRY_WAITING
                    && instanceRepository.transitionStatus(
                            execution.getWorkspaceId(),
                            execution.getId(),
                            DataSyncInstanceStatus.RETRY_WAITING,
                            DataSyncInstanceStatus.LOST,
                            null,
                            finishedAt,
                            errorCode,
                            message)) {
                retryWaiting++;
            }
            tableExecutionRepository.finishUnfinished(
                    execution.getWorkspaceId(), execution.getId(),
                    DataSyncTableExecutionStatus.LOST, finishedAt, errorCode, message);
        }
        LOG.warn(
                "旧数据同步运行状态已收口，lostExecutions={}, lostAttempts={}, lostTableAttempts={}, lostRetryWaiting={}",
                executions, attempts, tableAttempts, retryWaiting);
    }
}
