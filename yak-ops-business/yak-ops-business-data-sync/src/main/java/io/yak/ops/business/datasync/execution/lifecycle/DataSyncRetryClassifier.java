package io.yak.ops.business.datasync.execution.lifecycle;

import io.yak.ops.business.datasync.exception.DataSyncException;
import io.yak.ops.common.bean.vo.datasync.DataSyncDefinitionSnapshotVO;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLNonTransientException;
import java.sql.SQLRecoverableException;
import java.sql.SQLSyntaxErrorException;
import java.sql.SQLTransientException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;

/**
 * SMART Retry 的失败分类器。
 *
 * <p>这里只回答“这类失败是否值得自动重试”，不拥有 Attempt 状态迁移、最大次数或 durable backoff。
 * OFFLINE APPEND / OVERWRITE 一旦 Runtime 已启动，就按可能已产生目标副作用处理，不自动重放。</p>
 *
 * @author weifuwan
 * @since 2026-10-06
 */
public final class DataSyncRetryClassifier {

    /**
     * 分类异常失败。
     *
     * @param snapshot 当前 Execution 冻结定义
     * @param failure 原始失败异常
     * @param runtimeStarted YakFlow Runtime 是否已经启动
     * @param writeRows 当前 Attempt 已观察到的写入量
     * @return Retry 判定
     */
    public DataSyncRetryAssessment classify(
            DataSyncDefinitionSnapshotVO snapshot, Throwable failure, boolean runtimeStarted, long writeRows) {
        if (unsafeOfflineReplay(snapshot, runtimeStarted)) {
            return DataSyncRetryAssessment.stop("当前离线写入方式运行后可能已产生目标副作用");
        }
        if (failure == null) {
            return DataSyncRetryAssessment.stop("未识别到可重试的失败类型");
        }

        List<Throwable> causes = causeChain(failure);
        for (Throwable cause : causes) {
            if (cause instanceof InterruptedException || cause instanceof CancellationException) {
                return DataSyncRetryAssessment.stop("执行被中断或取消");
            }
            if (cause instanceof SQLIntegrityConstraintViolationException) {
                return DataSyncRetryAssessment.stop("数据库完整性约束错误");
            }
            if (cause instanceof SQLSyntaxErrorException) {
                return DataSyncRetryAssessment.stop("数据库 SQL 或对象定义错误");
            }
            if (cause instanceof SQLRecoverableException) {
                return DataSyncRetryAssessment.retryable("数据库连接可恢复异常");
            }
            if (cause instanceof SQLTransientException) {
                return DataSyncRetryAssessment.retryable("数据库瞬时异常");
            }
            if (cause instanceof SQLException sqlException) {
                DataSyncRetryAssessment sqlStateAssessment = classifySqlState(sqlException.getSQLState());
                if (sqlStateAssessment != null) return sqlStateAssessment;
                if (cause instanceof SQLNonTransientException) {
                    return DataSyncRetryAssessment.stop("数据库非瞬时异常");
                }
            }
            if (cause instanceof SocketTimeoutException || cause instanceof TimeoutException) {
                return DataSyncRetryAssessment.retryable("网络或执行超时");
            }
            if (cause instanceof ConnectException || cause instanceof SocketException) {
                return DataSyncRetryAssessment.retryable("网络连接瞬时异常");
            }
            if (cause instanceof UnsupportedOperationException || cause instanceof IllegalArgumentException) {
                return DataSyncRetryAssessment.stop("执行配置或能力不支持");
            }
            if (cause instanceof DataSyncException) {
                return DataSyncRetryAssessment.stop("数据同步产品校验失败");
            }
        }

        if (writeRows > 0) {
            return DataSyncRetryAssessment.stop("失败发生前已观察到目标写入，未知异常不自动重放");
        }
        return DataSyncRetryAssessment.stop("未知执行异常不自动重试");
    }

    /**
     * 连续 Source 没有抛异常却意外结束属于 REALTIME 可恢复信号。
     */
    public DataSyncRetryAssessment classifyUnexpectedContinuousEnd(DataSyncDefinitionSnapshotVO snapshot) {
        if (snapshot != null && DataSyncType.REALTIME.name().equals(snapshot.getSyncType())) {
            return DataSyncRetryAssessment.retryable("连续 Source 意外结束");
        }
        return DataSyncRetryAssessment.stop("非实时任务不应按连续 Source 结束重试");
    }

    private boolean unsafeOfflineReplay(DataSyncDefinitionSnapshotVO snapshot, boolean runtimeStarted) {
        if (!runtimeStarted || snapshot == null || !DataSyncType.OFFLINE.name().equals(snapshot.getSyncType())) {
            return false;
        }
        DataSyncWriteMode writeMode;
        try {
            writeMode = DataSyncWriteMode.valueOf(snapshot.getWriteMode());
        } catch (Exception exception) {
            return true;
        }
        return writeMode == DataSyncWriteMode.APPEND || writeMode == DataSyncWriteMode.OVERWRITE;
    }

    private DataSyncRetryAssessment classifySqlState(String sqlState) {
        if (sqlState == null || sqlState.length() < 2) return null;
        String stateClass = sqlState.substring(0, 2);
        return switch (stateClass) {
            case "08" -> DataSyncRetryAssessment.retryable("数据库连接异常");
            case "40" -> DataSyncRetryAssessment.retryable("数据库事务回滚，可重新执行");
            case "22" -> DataSyncRetryAssessment.stop("数据库数据异常");
            case "23" -> DataSyncRetryAssessment.stop("数据库完整性约束错误");
            case "28" -> DataSyncRetryAssessment.stop("数据库认证失败");
            case "42" -> DataSyncRetryAssessment.stop("数据库 SQL 或权限错误");
            default -> null;
        };
    }

    private List<Throwable> causeChain(Throwable failure) {
        List<Throwable> result = new ArrayList<>();
        Throwable current = failure;
        while (current != null && !result.contains(current)) {
            result.add(current);
            current = current.getCause();
        }
        Collections.reverse(result);
        return result;
    }
}
