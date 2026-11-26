package io.yak.ops.modeling.ddl.doris;

import io.yak.ops.modeling.ddl.core.AbstractDdlDialect;
import io.yak.ops.modeling.ddl.doris.execution.StatementExecutor;
import io.yak.ops.modeling.ddl.doris.model.DorisOptions;
import io.yak.ops.modeling.ddl.doris.model.DorisTableModel;
import io.yak.ops.modeling.ddl.doris.planning.SchemaPlanner;
import io.yak.ops.modeling.ddl.doris.schema.DdlGenerator;
import io.yak.ops.modeling.ddl.doris.schema.SchemaInspector;
import io.yak.ops.modeling.ddl.doris.validation.ModelValidator;
import io.yak.ops.modeling.ddl.model.DdlFieldTypeOption;
import io.yak.ops.modeling.ddl.model.DdlPlan;
import io.yak.ops.modeling.ddl.model.DdlPublishMode;
import io.yak.ops.modeling.ddl.model.DdlRequest;
import io.yak.ops.modeling.ddl.session.DdlSession;
import java.util.List;
import org.springframework.stereotype.Component;

/** Doris implementation of the common DDL dialect contract. */
@Component
public class DorisDialect extends AbstractDdlDialect {

    private static final long INCREMENTAL_SCHEMA_VERIFY_TIMEOUT_MILLIS = 30_000L;
    private static final long RECREATE_SCHEMA_VERIFY_TIMEOUT_MILLIS = 10_000L;
    private static final long PHYSICAL_SCHEMA_VERIFY_INTERVAL_MILLIS = 500L;

    private final ModelValidator validator;
    private final SchemaPlanner planner;
    private final StatementExecutor executor;
    private final SchemaInspector inspector;
    private final DdlGenerator generator;

    public DorisDialect(
            ModelValidator validator,
            SchemaPlanner planner,
            StatementExecutor executor,
            SchemaInspector inspector,
            DdlGenerator generator) {
        this.validator = validator;
        this.planner = planner;
        this.executor = executor;
        this.inspector = inspector;
        this.generator = generator;
    }

    @Override
    public String getDatabaseType() {
        return "DORIS";
    }

    @Override
    public List<DdlFieldTypeOption> getFieldTypeOptions() {
        return generator.getFieldTypeOptions();
    }

    @Override
    public void validate(DdlRequest request) {
        validator.validate(request);
    }

    @Override
    public DdlPlan plan(DdlSession session, DdlRequest request) {
        return planner.plan(session.getConnection(), session.getCatalog(), request);
    }

    @Override
    public String execute(DdlSession session, DdlRequest request, DdlPlan plan, String publishId)
            throws Exception {
        String database = request.getTable().getDatabase();
        String tableName = request.getTable().getTableName();
        executor.execute(session.getConnection(), plan, database, tableName, publishId);

        boolean recreate = request.getPublishMode() == DdlPublishMode.DROP_RECREATE;
        DorisTableModel expectedModel = request.getOptions(DorisOptions.class).getTableModel();
        long timeout = recreate
                ? RECREATE_SCHEMA_VERIFY_TIMEOUT_MILLIS
                : INCREMENTAL_SCHEMA_VERIFY_TIMEOUT_MILLIS;
        long deadline = System.currentTimeMillis() + timeout;
        SchemaInspector.VerificationResult lastResult;

        while (true) {
            lastResult = inspector.verify(
                    session.getConnection(),
                    session.getCatalog(),
                    request.getTable(),
                    expectedModel,
                    plan.getKeyFields(),
                    generator,
                    !recreate);
            if (lastResult.isMatched()) {
                return lastResult.getPhysicalSchema();
            }

            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                break;
            }
            try {
                Thread.sleep(Math.min(PHYSICAL_SCHEMA_VERIFY_INTERVAL_MILLIS, remaining));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new SchemaVerificationException(
                        "Doris DDL 已执行，但物理结构校验被中断。请先检查目标表，不要直接重复删除重建。",
                        lastResult.getPhysicalSchema(),
                        ex);
            }
        }

        String details = lastResult.getSummary();
        String message = recreate
                ? "Doris 已执行 DROP/CREATE，但新表物理结构校验未通过（等待 10 秒）。具体差异：" + details
                        + "。请先检查目标表，不要直接重复删除重建。"
                : "Doris DDL 已执行，但物理结构校验未通过（等待 30 秒）。具体差异：" + details;
        throw new SchemaVerificationException(message, lastResult.getPhysicalSchema());
    }
}
