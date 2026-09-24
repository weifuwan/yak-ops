package io.yak.ops.business.datasource.execution.audit;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.dao.entity.datasource.SqlExecutionAuditEntity;
import io.yak.ops.dao.entity.datasource.SqlStatementExecutionAuditEntity;
import io.yak.ops.dao.repository.datasource.SqlExecutionAuditRepository;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional persistence boundary for one completed SQL execution audit batch. */
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
