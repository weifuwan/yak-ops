package io.yak.ops.modeling.ddl.doris;

import io.yak.ops.dao.entity.enums.DorisDataModelEnum;
import io.yak.ops.dao.entity.enums.DorisPublishModeEnum;
import io.yak.ops.modeling.ddl.model.DdlColumnDefinition;
import io.yak.ops.modeling.ddl.model.DdlPlan;
import io.yak.ops.modeling.ddl.model.DorisColumnMetadata;
import io.yak.ops.modeling.ddl.model.DorisDdlRequest;
import io.yak.ops.modeling.ddl.model.DorisReplicationPlan;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceCatalog;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Creates a safe publish plan from the model definition and actual Doris schema. */
@Component
public class DorisSchemaPlanner {

    private final DorisSchemaInspector inspector;
    private final DorisDdlGenerator generator;
    private final DorisBackendInspector backendInspector;

    public DorisSchemaPlanner(DorisSchemaInspector inspector, DorisDdlGenerator generator) {
        this(inspector, generator, new DorisBackendInspector());
    }

    @Autowired
    public DorisSchemaPlanner(
            DorisSchemaInspector inspector,
            DorisDdlGenerator generator,
            DorisBackendInspector backendInspector) {
        this.inspector = inspector;
        this.generator = generator;
        this.backendInspector = backendInspector;
    }

    public DdlPlan plan(
            Connection connection, DataSourceCatalog catalog, DorisDdlRequest request) {
        List<String> keyFields = generator.resolveKeyFields(request.getTable());
        String qualifiedTable = "`" + request.getTable().getDatabase() + "`.`"
                + request.getTable().getTableName() + "`";
        List<String> statements = new ArrayList<>();

        if (request.getPublishMode() == DorisPublishModeEnum.DROP_RECREATE) {
            DorisReplicationPlan replication = backendInspector.resolve(
                    connection, request.getReplicationNum());
            String createDdl = generator.generateCreateTableDdl(
                    request.getTable(), request.getTableModel(), replication.getReplicationNum());
            statements.add("DROP TABLE IF EXISTS " + qualifiedTable);
            statements.add(createDdl);
            return createPlan(statements, keyFields, replication);
        }

        if (!inspector.tableExists(
                connection, catalog, request.getTable().getDatabase(), request.getTable().getTableName())) {
            DorisReplicationPlan replication = backendInspector.resolve(
                    connection, request.getReplicationNum());
            String createDdl = generator.generateCreateTableDdl(
                    request.getTable(), request.getTableModel(), replication.getReplicationNum());
            statements.add(createDdl);
            return createPlan(statements, keyFields, replication);
        }

        validatePreviousPublish(request, keyFields);

        Map<String, DorisColumnMetadata> actualColumns = inspector.readColumns(
                connection, catalog, request.getTable().getDatabase(), request.getTable().getTableName());
        if (actualColumns.isEmpty()) {
            throw new IllegalArgumentException(
                    "无法读取目标 Doris 表结构，增量发布已停止，请检查数据源权限或使用删除重建。");
        }

        Map<String, DdlColumnDefinition> expectedColumns = new LinkedHashMap<>();
        for (DdlColumnDefinition column : request.getTable().getColumns()) {
            expectedColumns.put(column.getName().toLowerCase(Locale.ROOT), column);
        }

        for (String actualName : actualColumns.keySet()) {
            if (!expectedColumns.containsKey(actualName)) {
                throw new IllegalArgumentException(
                        "目标表存在模型中已删除或未定义的字段: " + actualName
                                + "。增量发布不删除字段，请使用“删除重建”。");
            }
        }

        for (Map.Entry<String, DdlColumnDefinition> expected : expectedColumns.entrySet()) {
            DorisColumnMetadata actual = actualColumns.get(expected.getKey());
            DdlColumnDefinition column = expected.getValue();
            if (actual == null) {
                if (Boolean.FALSE.equals(column.getNullable())) {
                    throw new IllegalArgumentException(
                            "增量发布暂不支持新增 NOT NULL 字段，请使用“删除重建”以保证存量数据满足非空约束: " + column.getName());
                }
                statements.add(generator.generateAddColumnDdl(
                        request.getTable().getDatabase(), request.getTable().getTableName(), column));
            } else if (!generator.matches(actual, column)) {
                throw new IllegalArgumentException(
                        "字段结构发生不兼容变化: " + column.getName()
                                + "。增量发布不修改字段类型、长度或精度，请使用“删除重建”。");
            }
        }

        if (statements.isEmpty()) {
            return new DdlPlan(
                    "-- 当前目标表结构与模型一致，无需执行变更。",
                    statements,
                    true,
                    keyFields);
        }
        return new DdlPlan(joinStatements(statements), statements, false, keyFields);
    }

    private DdlPlan createPlan(
            List<String> statements, List<String> keyFields, DorisReplicationPlan replication) {
        return new DdlPlan(
                joinStatements(statements),
                statements,
                false,
                keyFields,
                replication.getReplicationNum(),
                replication.getAvailableBackendCount(),
                replication.isAutomatic(),
                replication.getWarning());
    }

    private void validatePreviousPublish(DorisDdlRequest request, List<String> keyFields) {
        DorisDataModelEnum previousModel = request.getPreviousTableModel();
        if (previousModel == null) {
            throw new IllegalArgumentException(
                    "目标 Doris 表已存在，但没有 Yak-OPS 成功发布记录。为保证增量发布安全，请选择“删除重建”。");
        }
        if (previousModel != request.getTableModel()) {
            throw new IllegalArgumentException("目标表模型与本次选择不一致，请使用“删除重建”切换表模型。");
        }
        if (!request.getPreviousKeyFields().equals(keyFields)) {
            throw new IllegalArgumentException(
                    "目标表 Key 字段定义已变化，增量发布不支持修改 Key 字段，请使用“删除重建”。");
        }
    }

    private String joinStatements(List<String> statements) {
        return String.join(";\n\n", statements) + ";";
    }
}
