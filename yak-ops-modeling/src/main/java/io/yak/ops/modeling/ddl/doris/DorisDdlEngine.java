package io.yak.ops.modeling.ddl.doris;

import io.yak.ops.modeling.ddl.model.DdlPlan;
import io.yak.ops.modeling.ddl.model.DorisDdlRequest;
import io.yak.ops.modeling.ddl.model.DorisFieldTypeOption;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceCatalog;
import java.sql.Connection;
import java.util.List;
import org.springframework.stereotype.Component;

/** Public entry point for Doris DDL planning, execution and post-execution verification. */
@Component
public class DorisDdlEngine {

    private static final long PHYSICAL_SCHEMA_VERIFY_TIMEOUT_MILLIS = 30_000L;
    private static final long PHYSICAL_SCHEMA_VERIFY_INTERVAL_MILLIS = 1_000L;

    private final DorisDdlValidator validator;
    private final DorisSchemaPlanner planner;
    private final DorisDdlExecutor executor;
    private final DorisSchemaInspector inspector;
    private final DorisDdlGenerator generator;

    public DorisDdlEngine(
            DorisDdlValidator validator,
            DorisSchemaPlanner planner,
            DorisDdlExecutor executor,
            DorisSchemaInspector inspector,
            DorisDdlGenerator generator) {
        this.validator = validator;
        this.planner = planner;
        this.executor = executor;
        this.inspector = inspector;
        this.generator = generator;
    }

    public List<DorisFieldTypeOption> getFieldTypeOptions() {
        return generator.getFieldTypeOptions();
    }

    public DdlPlan plan(
            Connection connection, DataSourceCatalog catalog, DorisDdlRequest request) {
        validator.validate(request);
        return planner.plan(connection, catalog, request);
    }

    /** Executes the plan, verifies the live schema and returns its SHOW CREATE TABLE baseline. */
    public String execute(
            Connection connection, DataSourceCatalog catalog, DorisDdlRequest request,
            DdlPlan plan, String publishId)
            throws Exception {
        executor.execute(
                connection, plan, request.getTable().getDatabase(), request.getTable().getTableName(), publishId);
        long deadline = System.currentTimeMillis() + PHYSICAL_SCHEMA_VERIFY_TIMEOUT_MILLIS;
        RuntimeException lastInspectionError = null;
        boolean matched = false;
        while (System.currentTimeMillis() <= deadline) {
            try {
                matched = inspector.matches(
                        connection,
                        catalog,
                        request.getTable(),
                        request.getTableModel(),
                        plan.getKeyFields(),
                        generator);
                if (matched) {
                    break;
                }
                lastInspectionError = null;
            } catch (RuntimeException inspectionError) {
                lastInspectionError = inspectionError;
            }
            Thread.sleep(PHYSICAL_SCHEMA_VERIFY_INTERVAL_MILLIS);
        }
        if (!matched) {
            throw new IllegalStateException(
                    "DDL 执行后目标 Doris 物理结构在 30 秒内未与当前模型一致，请检查 Schema Change 状态。",
                    lastInspectionError);
        }
        return inspector.readCreateTableDdl(
                connection, request.getTable().getDatabase(), request.getTable().getTableName());
    }
}
