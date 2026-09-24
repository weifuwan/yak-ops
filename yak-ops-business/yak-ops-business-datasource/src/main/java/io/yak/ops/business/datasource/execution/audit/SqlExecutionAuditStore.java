package io.yak.ops.business.datasource.execution.audit;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.dao.SqlExecutionAuditDao;
import io.yak.ops.business.datasource.dao.model.SqlExecutionAuditPO;
import io.yak.ops.business.datasource.dao.model.SqlStatementExecutionAuditPO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional persistence boundary for one completed SQL execution audit batch. */
@Service
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class SqlExecutionAuditStore {

    private final SqlExecutionAuditDao auditDao;

    @Transactional(transactionManager = "opsDataSourceTransactionManager", rollbackFor = Exception.class)
    public void save(SqlExecutionAuditPO execution, List<SqlStatementExecutionAuditPO> statements) {
        auditDao.insertExecution(execution);
        auditDao.insertStatements(statements);
    }
}
