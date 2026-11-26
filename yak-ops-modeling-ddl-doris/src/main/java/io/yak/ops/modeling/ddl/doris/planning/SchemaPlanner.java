package io.yak.ops.modeling.ddl.doris.planning;

import io.yak.ops.modeling.ddl.doris.model.DorisOptions;
import io.yak.ops.modeling.ddl.doris.model.DorisPhysicalTable;
import io.yak.ops.modeling.ddl.doris.model.DorisTableModel;
import io.yak.ops.modeling.ddl.doris.model.ReplicationPlan;
import io.yak.ops.modeling.ddl.doris.schema.DdlGenerator;
import io.yak.ops.modeling.ddl.doris.schema.SchemaInspector;
import io.yak.ops.modeling.ddl.model.DdlColumnDefinition;
import io.yak.ops.modeling.ddl.model.DdlColumnMetadata;
import io.yak.ops.modeling.ddl.model.DdlPlan;
import io.yak.ops.modeling.ddl.model.DdlPublishMode;
import io.yak.ops.modeling.ddl.model.DdlRequest;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceCatalog;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Creates a safe publish plan from the logical model and live Doris physical schema. */
@Component
public class SchemaPlanner {

    private final SchemaInspector inspector;
    private final DdlGenerator generator;
    private final ReplicationPolicy replicationPolicy;

    public SchemaPlanner(SchemaInspector inspector, DdlGenerator generator) {
        this(inspector, generator, new ReplicationPolicy());
    }

    public SchemaPlanner(
            SchemaInspector inspector, DdlGenerator generator, ReplicationPolicy replicationPolicy) {
        this.inspector = inspector;
        this.generator = generator;
        this.replicationPolicy = replicationPolicy;
    }

    public DdlPlan plan(Connection connection, DataSourceCatalog catalog, DdlRequest request) {
        DorisOptions options = request.getOptions(DorisOptions.class);
        DorisTableModel tableModel = options.getTableModel();
        List<String> keyFields = generator.resolveKeyFields(request.getTable(), tableModel);
        String database = request.getTable().getDatabase();
        String tableName = request.getTable().getTableName();
        String qualifiedTable = "`" + database.replace("`", "``") + "`.`"
                + tableName.replace("`", "``") + "`";
        List<String> statements = new ArrayList<>();

        if (request.getPublishMode() == DdlPublishMode.DROP_RECREATE) {
            boolean exists = inspector.tableExists(connection, catalog, database, tableName);
            ReplicationPlan replication = replicationPolicy.resolve(connection, options.getReplicationNum());
            statements.add("DROP TABLE IF EXISTS " + qualifiedTable);
            statements.add(generator.generateCreateTableDdl(
                    request.getTable(), tableModel, replication.getReplicationNum()));
            return createPlan(statements, keyFields, replication, exists);
        }

        DorisPhysicalTable physical = inspector.inspect(connection, catalog, database, tableName);
        if (physical == null) {
            ReplicationPlan replication = replicationPolicy.resolve(connection, options.getReplicationNum());
            statements.add(generator.generateCreateTableDdl(
                    request.getTable(), tableModel, replication.getReplicationNum()));
            return createPlan(statements, keyFields, replication, false);
        }

        validatePhysicalTableModel(physical, tableModel, keyFields);
        boolean schemaDrift = hasSchemaDrift(options, physical);

        Map<String, DdlColumnDefinition> expectedColumns = new LinkedHashMap<>();
        for (DdlColumnDefinition column : request.getTable().getColumns()) {
            expectedColumns.put(column.getName().toLowerCase(Locale.ROOT), column);
        }

        List<String> addedColumns = new ArrayList<>();
        List<String> droppedColumns = new ArrayList<>();

        for (Map.Entry<String, DdlColumnMetadata> actualEntry : physical.getColumns().entrySet()) {
            if (expectedColumns.containsKey(actualEntry.getKey())) {
                continue;
            }
            DdlColumnMetadata actualColumn = actualEntry.getValue();
            if (actualColumn.isPrimaryKey() || physical.getProtectedColumns().contains(actualEntry.getKey())) {
                throw new IllegalArgumentException(
                        "目标表存在模型中已删除的 Key、分区或分桶字段: " + actualColumn.getName()
                                + "。此类字段不能通过增量发布删除，请使用“删除重建”。");
            }
            droppedColumns.add(actualColumn.getName());
            statements.add(generator.generateDropColumnDdl(database, tableName, actualColumn.getName()));
        }

        for (Map.Entry<String, DdlColumnDefinition> expected : expectedColumns.entrySet()) {
            DdlColumnMetadata actual = physical.getColumns().get(expected.getKey());
            DdlColumnDefinition column = expected.getValue();
            if (actual == null) {
                if (column.isPrimaryKey() || containsIdentifier(keyFields, column.getName())) {
                    throw new IllegalArgumentException(
                            "增量发布不支持新增 Doris Key 字段: " + column.getName()
                                    + "，请使用“删除重建”。");
                }
                if (Boolean.FALSE.equals(column.getNullable())) {
                    throw new IllegalArgumentException(
                            "增量发布暂不支持新增 NOT NULL 字段，请先设置为可空或使用“删除重建”: "
                                    + column.getName());
                }
                statements.add(generator.generateAddColumnDdl(database, tableName, column));
                addedColumns.add(column.getName());
            } else if (!generator.matches(actual, column)) {
                throw new IllegalArgumentException(
                        "字段结构发生不兼容变化: " + column.getName()
                                + "。增量发布暂不自动修改字段类型、长度、精度或 NULL 属性，请使用“删除重建”。");
            }
        }

        boolean noChanges = statements.isEmpty();
        String ddl = noChanges
                ? "-- 当前 Doris 物理表结构与模型一致，无需执行变更。"
                : String.join(";\n\n", statements) + ";";
        return new DdlPlan(
                ddl,
                statements,
                noChanges,
                keyFields,
                true,
                addedColumns,
                droppedColumns,
                !droppedColumns.isEmpty(),
                schemaDrift,
                physical.getCreateDdl(),
                null);
    }

    private void validatePhysicalTableModel(
            DorisPhysicalTable physical, DorisTableModel expectedModel, List<String> expectedKeyFields) {
        if (physical.getTableModel() != expectedModel) {
            throw new IllegalArgumentException(
                    "Doris 物理表使用 " + physical.getTableModel()
                            + " KEY，与当前选择的 " + expectedModel
                            + " 不一致。增量发布不能切换表模型，请使用“删除重建”。");
        }
        if (!sameIdentifiers(physical.getKeyFields(), expectedKeyFields)) {
            throw new IllegalArgumentException(
                    "Doris 物理表 Key 字段与当前模型不一致。增量发布不能修改 Key 定义，请使用“删除重建”。");
        }
    }

    private boolean hasSchemaDrift(DorisOptions options, DorisPhysicalTable physical) {
        if (options.getPreviousTableModel() != null
                && options.getPreviousTableModel() != physical.getTableModel()) {
            return true;
        }
        if (!options.getPreviousKeyFields().isEmpty()
                && !sameIdentifiers(options.getPreviousKeyFields(), physical.getKeyFields())) {
            return true;
        }
        String previousSchema = normalizeSchema(options.getPreviousPhysicalSchema());
        return previousSchema != null
                && !previousSchema.equals(normalizeSchema(physical.getCreateDdl()));
    }

    private String normalizeSchema(String schema) {
        if (schema == null || schema.trim().isEmpty()) {
            return null;
        }
        return schema.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private boolean containsIdentifier(List<String> identifiers, String target) {
        return identifiers != null && identifiers.stream().anyMatch(value -> value.equalsIgnoreCase(target));
    }

    private boolean sameIdentifiers(List<String> left, List<String> right) {
        if (left == null || right == null || left.size() != right.size()) {
            return false;
        }
        for (int index = 0; index < left.size(); index++) {
            if (!left.get(index).equalsIgnoreCase(right.get(index))) {
                return false;
            }
        }
        return true;
    }

    private DdlPlan createPlan(
            List<String> statements, List<String> keyFields, ReplicationPlan replication,
            boolean targetTableExists) {
        return new DdlPlan(
                String.join(";\n\n", statements) + ";",
                statements,
                false,
                keyFields,
                targetTableExists,
                Collections.<String>emptyList(),
                Collections.<String>emptyList(),
                false,
                false,
                null,
                replication);
    }
}
