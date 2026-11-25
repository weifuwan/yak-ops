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

    public void execute(
            Connection connection, DataSourceCatalog catalog, DorisDdlRequest request,
            DdlPlan plan, String publishId)
            throws Exception {
        executor.execute(
                connection, plan, request.getTable().getDatabase(), request.getTable().getTableName(), publishId);
        if (!inspector.matches(connection, catalog, request.getTable(), generator)) {
            throw new IllegalStateException(
                    "DDL 执行后目标 Doris 表结构与当前模型不一致，请检查目标库中的表定义。");
        }
    }
}
