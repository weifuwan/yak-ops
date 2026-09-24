package io.yak.ops.dao.repository.datasource;

import io.yak.ops.common.PageData;
import io.yak.ops.dao.entity.datasource.SqlExecutionAuditEntity;
import io.yak.ops.dao.entity.datasource.SqlStatementExecutionAuditEntity;
import io.yak.ops.dao.model.datasource.SqlExecutionAuditSummaryRow;
import io.yak.ops.dao.model.datasource.SqlStatementTypeCountRow;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 定义 SQL execution 审计记录的持久化读写和聚合查询能力。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
public interface SqlExecutionAuditRepository {

    /** 写入一次 SQL execution 审计记录。 */
    void insertExecution(SqlExecutionAuditEntity execution);

    /** 写入一次 SQL execution 下的 Statement 审计记录。 */
    void insertStatements(List<SqlStatementExecutionAuditEntity> statements);

    /** 按审计筛选条件查询执行记录分页。 */
    PageData<SqlExecutionAuditEntity> queryPage(Query query);

    /** 按 executionId 查询单次执行审计记录。 */
    SqlExecutionAuditEntity queryByExecutionId(String executionId);

    /** 按 executionId 查询 Statement 审计记录并保持执行顺序。 */
    List<SqlStatementExecutionAuditEntity> queryStatements(String executionId);

    /** 按审计筛选条件查询聚合统计。 */
    SqlExecutionAuditSummaryRow querySummary(Query query);

    /** 按审计筛选条件查询执行耗时 P95，单位毫秒。 */
    long queryP95DurationMs(Query query);

    /** 按审计筛选条件查询 Statement 类型分布。 */
    List<SqlStatementTypeCountRow> queryStatementTypeCounts(Query query);

    /**
     * SQL execution 审计查询条件，枚举值使用数据库持久化名称表达。
     *
     * @param pageNo 当前页码，从 1 开始
     * @param pageSize 每页数量，最大 200
     * @param executionId 执行唯一标识
     * @param dataSourceId 数据源标识
     * @param caller 调用方枚举名称
     * @param callerReference 调用方业务引用
     * @param operatorName 操作人标识
     * @param status 执行状态枚举名称
     * @param transactionMode 事务模式枚举名称
     * @param statementType Statement 类型枚举名称
     * @param sqlFingerprint SQL 指纹
     * @param minDurationMs 最小执行耗时，单位毫秒
     * @param startedFrom 执行开始时间下界
     * @param startedTo 执行开始时间上界
     * @author weifuwan
     * @since 2026-09-24
     */
    record Query(
            int pageNo,
            int pageSize,
            String executionId,
            String dataSourceId,
            String caller,
            String callerReference,
            String operatorName,
            String status,
            String transactionMode,
            String statementType,
            String sqlFingerprint,
            Long minDurationMs,
            LocalDateTime startedFrom,
            LocalDateTime startedTo) {

        public Query {
            pageNo = Math.max(1, pageNo);
            pageSize = Math.min(200, Math.max(1, pageSize));
            executionId = normalize(executionId);
            dataSourceId = normalize(dataSourceId);
            caller = normalize(caller);
            callerReference = normalize(callerReference);
            operatorName = normalize(operatorName);
            status = normalize(status);
            transactionMode = normalize(transactionMode);
            statementType = normalize(statementType);
            sqlFingerprint = normalize(sqlFingerprint);
            minDurationMs = minDurationMs == null ? null : Math.max(0L, minDurationMs);
        }

        private static String normalize(String value) {
            if (value == null || value.isBlank()) return null;
            return value.trim();
        }
    }
}
