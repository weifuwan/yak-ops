package io.yak.ops.business.datasource.execution.audit;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.dao.entity.datasource.SqlExecutionAuditEntity;
import io.yak.ops.dao.entity.datasource.SqlStatementExecutionAuditEntity;
import io.yak.ops.dao.repository.datasource.SqlExecutionAuditRepository;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 在单个事务中保存一次完整 SQL execution 及其 Statement 审计记录。
 *
 * @author weifuwan
 * @since 2026-08-18
 */
@Service
@ConditionalOnDataSourceEnabled
public class SqlExecutionAuditStore {

    @Resource
    private SqlExecutionAuditRepository auditRepository;

    @Transactional(transactionManager = "opsDataSourceTransactionManager", rollbackFor = Exception.class)
    public void save(SqlExecutionAuditEntity execution, List<SqlStatementExecutionAuditEntity> statements) {
        auditRepository.insertExecution(execution);
        auditRepository.insertStatements(statements);
    }
}
