package io.yak.ops.dao.repository.datasource.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.common.PageData;
import io.yak.ops.dao.entity.datasource.SqlExecutionAuditEntity;
import io.yak.ops.dao.entity.datasource.SqlStatementExecutionAuditEntity;
import io.yak.ops.dao.mapper.datasource.SqlExecutionAuditMapper;
import io.yak.ops.dao.mapper.datasource.SqlStatementExecutionAuditMapper;
import io.yak.ops.dao.model.datasource.SqlExecutionAuditSummaryRow;
import io.yak.ops.dao.model.datasource.SqlStatementTypeCountRow;
import io.yak.ops.dao.repository.datasource.SqlExecutionAuditRepository;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 使用 MyBatis-Plus 实现 SQL execution 审计记录和统计查询。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Repository
public class SqlExecutionAuditRepositoryImpl implements SqlExecutionAuditRepository {

    @Resource
    private SqlExecutionAuditMapper executionMapper;

    @Resource
    private SqlStatementExecutionAuditMapper statementMapper;

    @Override
    public void insertExecution(SqlExecutionAuditEntity execution) {
        if (execution == null) throw new IllegalArgumentException("execution must not be null");
        executionMapper.insert(execution);
    }

    @Override
    public void insertStatements(List<SqlStatementExecutionAuditEntity> statements) {
        if (statements == null || statements.isEmpty()) return;
        for (SqlStatementExecutionAuditEntity statement : statements) statementMapper.insert(statement);
    }

    @Override
    public PageData<SqlExecutionAuditEntity> queryPage(Query query) {
        Query condition = requireQuery(query);
        IPage<SqlExecutionAuditEntity> page =
                executionMapper.selectAuditPage(Page.of(condition.pageNo(), condition.pageSize()), condition);
        return new PageData<>(
                page.getRecords(), page.getTotal(), page.getPages(), page.getCurrent(), page.getSize());
    }

    @Override
    public SqlExecutionAuditEntity queryByExecutionId(String executionId) {
        if (executionId == null || executionId.isBlank()) return null;
        return executionMapper.selectOne(Wrappers.<SqlExecutionAuditEntity>lambdaQuery()
                .eq(SqlExecutionAuditEntity::getExecutionId, executionId.trim())
                .last("LIMIT 1"));
    }

    @Override
    public List<SqlStatementExecutionAuditEntity> queryStatements(String executionId) {
        if (executionId == null || executionId.isBlank()) return List.of();
        if (queryByExecutionId(executionId) == null) return List.of();
        return statementMapper.selectList(Wrappers.<SqlStatementExecutionAuditEntity>lambdaQuery()
                .eq(SqlStatementExecutionAuditEntity::getExecutionId, executionId.trim())
                .orderByAsc(SqlStatementExecutionAuditEntity::getStatementIndex)
                .orderByAsc(SqlStatementExecutionAuditEntity::getId));
    }

    @Override
    public SqlExecutionAuditSummaryRow querySummary(Query query) {
        SqlExecutionAuditSummaryRow row = executionMapper.selectAuditSummary(requireQuery(query));
        return row == null ? new SqlExecutionAuditSummaryRow() : row;
    }

    @Override
    public long queryP95DurationMs(Query query) {
        Long value = executionMapper.selectP95DurationMs(requireQuery(query));
        return value == null ? 0L : Math.max(0L, value);
    }

    @Override
    public List<SqlStatementTypeCountRow> queryStatementTypeCounts(Query query) {
        List<SqlStatementTypeCountRow> rows = executionMapper.selectStatementTypeCounts(requireQuery(query));
        return rows == null ? List.of() : List.copyOf(rows);
    }

    private static Query requireQuery(Query query) {
        if (query == null) throw new IllegalArgumentException("query must not be null");
        return query;
    }
}
