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

    private static final long INCREMENTAL_SCHEMA_VERIFY_TIMEOUT_MILLIS = 30_000L;
    private static final long RECREATE_SCHEMA_VERIFY_TIMEOUT_MILLIS = 10_000L;
    private static final long PHYSICAL_SCHEMA_VERIFY_INTERVAL_MILLIS = 500L;

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

    /**
     * Executes DDL and applies a verification policy matching the publish mode.
     * DROP_RECREATE does not wait for Schema Change and does not reject metadata-only
     * nullability differences; it verifies the newly created table model, Key and fields.
     */
    public String execute(
            Connection connection, DataSourceCatalog catalog, DorisDdlRequest request,
            DdlPlan plan, String publishId)
            throws Exception {
        executor.execute(
                connection, plan, request.getTable().getDatabase(), request.getTable().getTableName(), publishId);

        boolean recreate = request.getPublishMode() == io.yak.ops.dao.entity.enums.DorisPublishModeEnum.DROP_RECREATE;
        boolean strictNullability = !recreate;
        long timeout = recreate
                ? RECREATE_SCHEMA_VERIFY_TIMEOUT_MILLIS
                : INCREMENTAL_SCHEMA_VERIFY_TIMEOUT_MILLIS;
        long deadline = System.currentTimeMillis() + timeout;
        DorisSchemaInspector.VerificationResult lastResult = null;

        while (true) {
            lastResult = inspector.verify(
                    connection,
                    catalog,
                    request.getTable(),
                    request.getTableModel(),
                    plan.getKeyFields(),
                    generator,
                    strictNullability);
            if (lastResult.isMatched()) {
                return lastResult.getPhysicalSchema();
            }
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                break;
            }
            Thread.sleep(Math.min(PHYSICAL_SCHEMA_VERIFY_INTERVAL_MILLIS, remaining));
        }

        String details = lastResult == null ? "未能读取目标表元数据" : lastResult.getSummary();
        String message = recreate
                ? "Doris 已执行 DROP/CREATE，但新表物理结构校验未通过（等待 10 秒）。具体差异：" + details
                        + "。请先检查目标表，不要直接重复删除重建。"
                : "Doris DDL 已执行，但物理结构校验未通过（等待 30 秒）。具体差异：" + details;
        throw new DorisSchemaVerificationException(
                message, lastResult == null ? null : lastResult.getPhysicalSchema());
    }
}
