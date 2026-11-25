package io.yak.ops.modeling.ddl.doris;

import io.yak.ops.modeling.ddl.model.DdlPlan;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Executes a previously validated Doris DDL plan in order. */
@Component
public class DorisDdlExecutor {

    private static final Logger log = LoggerFactory.getLogger(DorisDdlExecutor.class);

    public void execute(
            Connection connection, DdlPlan plan, String database, String tableName, String publishId)
            throws Exception {
        if (plan.isNoChanges() || plan.getStatements().isEmpty()) {
            log.info(
                    "Doris DDL plan contains no changes: publishId={}, database={}, table={}",
                    publishId, database, tableName);
            return;
        }
        try (Statement statement = connection.createStatement()) {
            int total = plan.getStatements().size();
            for (int index = 0; index < total; index++) {
                String sql = plan.getStatements().get(index);
                log.info(
                        "Executing Doris DDL step {}/{}: publishId={}, database={}, table={}, operation={}",
                        index + 1,
                        total,
                        publishId,
                        database,
                        tableName,
                        operationOf(sql));
                statement.execute(sql);
            }
        }
    }

    private String operationOf(String sql) {
        if (sql == null) {
            return "UNKNOWN";
        }
        String normalized = sql.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("DROP TABLE")) return "DROP_TABLE";
        if (normalized.startsWith("CREATE TABLE")) return "CREATE_TABLE";
        if (normalized.startsWith("ALTER TABLE")) return "ALTER_TABLE";
        return "DDL";
    }
}
