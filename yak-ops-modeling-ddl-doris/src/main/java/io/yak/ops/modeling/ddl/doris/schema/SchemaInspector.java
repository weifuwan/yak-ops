package io.yak.ops.modeling.ddl.doris.schema;

import io.yak.ops.modeling.ddl.doris.model.DorisTableModel;
import io.yak.ops.modeling.ddl.doris.schema.DdlGenerator;
import io.yak.ops.modeling.ddl.model.DdlColumnDefinition;
import io.yak.ops.modeling.ddl.model.DdlTableDefinition;
import io.yak.ops.modeling.ddl.model.DdlColumnMetadata;
import io.yak.ops.modeling.ddl.doris.model.DorisPhysicalTable;
import io.yak.ops.plugin.datasource.api.catalog.CatalogObject;
import io.yak.ops.plugin.datasource.api.catalog.CatalogObjectType;
import io.yak.ops.plugin.datasource.api.catalog.CatalogPath;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceCatalog;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceCatalogColumn;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Reads actual Doris table metadata through SHOW statements and the datasource catalog SPI. */
@Component
public class SchemaInspector {

    private static final Pattern TABLE_MODEL_PATTERN = Pattern.compile(
            "(?is)\\b(UNIQUE|DUPLICATE|AGGREGATE|PRIMARY)\\s+KEY\\s*\\(([^)]*)\\)");
    private static final Pattern QUOTED_IDENTIFIER_PATTERN = Pattern.compile("`([^`]+)`");
    private static final Pattern PROPERTY_PATTERN = Pattern.compile(
            "(?is)[\"']([^\"']+)[\"']\\s*=\\s*[\"']([^\"']*)[\"']");

    public boolean tableExists(
            Connection connection, DataSourceCatalog catalog, String database, String tableName) {
        CatalogObject object = catalog.getObject(
                connection,
                new CatalogPath(null, database, null, tableName, CatalogObjectType.TABLE));
        return object != null && object.getType() == CatalogObjectType.TABLE;
    }

    public Map<String, DdlColumnMetadata> readColumns(
            Connection connection, DataSourceCatalog catalog, String database, String tableName) {
        List<DataSourceCatalogColumn> columns = catalog.listColumns(
                connection, new CatalogPath(null, database, null, tableName, CatalogObjectType.TABLE));
        Map<String, DdlColumnMetadata> result = new LinkedHashMap<>();
        if (columns == null) {
            return result;
        }
        for (DataSourceCatalogColumn column : columns) {
            if (column == null || column.getName() == null || column.getName().trim().isEmpty()) {
                continue;
            }
            result.put(column.getName().toLowerCase(Locale.ROOT), new DdlColumnMetadata(
                    column.getName(),
                    column.getTypeName(),
                    column.getSize(),
                    column.getScale(),
                    column.getNullable(),
                    column.isPrimaryKey()));
        }
        return result;
    }

    public DorisPhysicalTable inspect(
            Connection connection, DataSourceCatalog catalog, String database, String tableName) {
        if (!tableExists(connection, catalog, database, tableName)) {
            return null;
        }
        String createDdl = readCreateTableDdl(connection, database, tableName);
        Map<String, DdlColumnMetadata> columns = readColumns(connection, catalog, database, tableName);
        if (columns.isEmpty()) {
            throw new IllegalStateException(
                    "已识别目标 Doris 表，但无法读取字段结构: " + database + "." + tableName);
        }

        Matcher modelMatcher = TABLE_MODEL_PATTERN.matcher(createDdl);
        if (!modelMatcher.find()) {
            throw new IllegalArgumentException(
                    "无法识别 Doris 物理表的 Key 模型，暂不支持该表结构的增量发布。");
        }
        String modelName = modelMatcher.group(1).toUpperCase(Locale.ROOT);
        if (!"UNIQUE".equals(modelName) && !"DUPLICATE".equals(modelName)) {
            throw new IllegalArgumentException(
                    "目标 Doris 表使用 " + modelName + " KEY，当前维度建模仅支持 UNIQUE KEY 和 DUPLICATE KEY。");
        }
        DorisTableModel tableModel = DorisTableModel.valueOf(modelName);
        List<String> keyFields = parseIdentifiers(modelMatcher.group(2));
        String partitionClause = extractClause(
                createDdl, "PARTITION BY", "(?=\\s+DISTRIBUTED BY\\s+|\\s+PROPERTIES\\s*\\(|$)");
        String distributionClause = extractClause(
                createDdl, "DISTRIBUTED BY", "(?=\\s+PROPERTIES\\s*\\(|$)");
        Map<String, String> properties = parseProperties(createDdl);

        Set<String> protectedColumns = new LinkedHashSet<>();
        keyFields.forEach(name -> protectedColumns.add(name.toLowerCase(Locale.ROOT)));
        protectedColumns.addAll(parseDistributionColumns(distributionClause));
        protectedColumns.addAll(parseIdentifiersFromClause(partitionClause));

        return new DorisPhysicalTable(
                createDdl,
                tableModel,
                keyFields,
                columns,
                partitionClause,
                distributionClause,
                properties,
                protectedColumns);
    }

    public String readCreateTableDdl(Connection connection, String database, String tableName) {
        String sql = "SHOW CREATE TABLE " + quoteIdentifier(database) + "." + quoteIdentifier(tableName);
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            if (!result.next()) {
                throw new IllegalStateException("Doris 表存在性检查成功，但 SHOW CREATE TABLE 未返回结果");
            }
            String ddl = result.getString(2);
            if (ddl == null) {
                ddl = result.getString("Create Table");
            }
            if (ddl == null || ddl.trim().isEmpty()) {
                throw new IllegalStateException("Doris SHOW CREATE TABLE 未返回建表语句");
            }
            return ddl;
        } catch (SQLException ex) {
            throw new IllegalStateException(
                    "读取 Doris 物理表定义失败: " + database + "." + tableName, ex);
        }
    }

    /**
     * Verifies the live table with a mode-specific policy. Recreate verification checks the
     * table model, Key columns and physical field names/types; incremental verification also
     * checks nullability because it must protect an existing table from silent drift.
     */
    public VerificationResult verify(
            Connection connection,
            DataSourceCatalog catalog,
            DdlTableDefinition table,
            DorisTableModel expectedModel,
            List<String> expectedKeyFields,
            DdlGenerator generator,
            boolean strictNullability) {
        String physicalSchema = null;
        List<String> mismatches = new ArrayList<>();
        try {
            if (!tableExists(connection, catalog, table.getDatabase(), table.getTableName())) {
                mismatches.add("目标表不存在: " + table.getDatabase() + "." + table.getTableName());
                return new VerificationResult(false, mismatches, null);
            }

            physicalSchema = readCreateTableDdl(
                    connection, table.getDatabase(), table.getTableName());
            Map<String, DdlColumnMetadata> actualColumns = readColumns(
                    connection, catalog, table.getDatabase(), table.getTableName());
            if (actualColumns.isEmpty()) {
                mismatches.add("Doris 已返回目标表，但暂时无法读取字段元数据");
                return new VerificationResult(false, mismatches, physicalSchema);
            }

            Matcher modelMatcher = TABLE_MODEL_PATTERN.matcher(physicalSchema);
            if (!modelMatcher.find()) {
                mismatches.add("无法从 SHOW CREATE TABLE 识别 Doris Key 模型");
            } else {
                String actualModel = modelMatcher.group(1).toUpperCase(Locale.ROOT);
                if (!expectedModel.name().equals(actualModel)) {
                    mismatches.add("表模型不一致：模型期望 " + expectedModel
                            + "，Doris 实际为 " + actualModel);
                }
                List<String> actualKeyFields = parseIdentifiers(modelMatcher.group(2));
                if (!sameIdentifiers(actualKeyFields, expectedKeyFields)) {
                    mismatches.add("Key 字段不一致：模型期望 " + expectedKeyFields
                            + "，Doris 实际为 " + actualKeyFields);
                }
            }

            Map<String, DdlColumnDefinition> expectedColumns = new LinkedHashMap<>();
            for (DdlColumnDefinition expected : table.getColumns()) {
                expectedColumns.put(expected.getName().toLowerCase(Locale.ROOT), expected);
                DdlColumnMetadata actual = actualColumns.get(
                        expected.getName().toLowerCase(Locale.ROOT));
                if (actual == null) {
                    mismatches.add("缺少字段: " + expected.getName());
                    continue;
                }
                if (!generator.matchesType(actual, expected)) {
                    mismatches.add("字段类型不一致: " + expected.getName()
                            + "（模型期望 " + generator.toDorisType(expected)
                            + "，Doris 实际为 " + actual.getTypeName() + "）");
                } else if (strictNullability && !generator.matches(actual, expected)) {
                    mismatches.add("字段 NULL 属性不一致: " + expected.getName()
                            + "（模型期望 " + nullabilityLabel(expected.getNullable())
                            + "，Doris 实际为 " + nullabilityLabel(actual.getNullable()) + "）");
                }
            }

            for (Map.Entry<String, DdlColumnMetadata> actual : actualColumns.entrySet()) {
                if (!expectedColumns.containsKey(actual.getKey())) {
                    mismatches.add("存在模型中未定义的字段: " + actual.getValue().getName());
                }
            }
        } catch (RuntimeException ex) {
            mismatches.add("读取物理结构失败: " + errorMessage(ex));
        }
        return new VerificationResult(mismatches.isEmpty(), mismatches, physicalSchema);
    }

    /** Backward-compatible strict comparison used by existing callers and tests. */
    public boolean matches(
            Connection connection,
            DataSourceCatalog catalog,
            DdlTableDefinition table,
            DorisTableModel expectedModel,
            List<String> expectedKeyFields,
            DdlGenerator generator) {
        return verify(connection, catalog, table, expectedModel, expectedKeyFields, generator, true)
                .isMatched();
    }

    private String nullabilityLabel(Boolean nullable) {
        return nullable == null ? "未知" : nullable ? "NULL" : "NOT NULL";
    }

    private String errorMessage(Throwable exception) {
        String message = exception == null ? null : exception.getMessage();
        return message == null || message.trim().isEmpty()
                ? exception == null ? "未知异常" : exception.getClass().getSimpleName()
                : message;
    }

    public static final class VerificationResult {
        private final boolean matched;
        private final List<String> mismatches;
        private final String physicalSchema;

        public VerificationResult(boolean matched, List<String> mismatches, String physicalSchema) {
            this.matched = matched;
            this.mismatches = mismatches == null
                    ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(mismatches));
            this.physicalSchema = physicalSchema;
        }

        public boolean isMatched() {
            return matched;
        }

        public List<String> getMismatches() {
            return mismatches;
        }

        public String getPhysicalSchema() {
            return physicalSchema;
        }

        public String getSummary() {
            return mismatches.isEmpty() ? "未发现具体差异" : String.join("；", mismatches);
        }
    }

    private String extractClause(String ddl, String keyword, String lookAhead) {
        Matcher matcher = Pattern.compile(
                "(?is)\\b" + keyword + "\\s+(.+?)" + lookAhead).matcher(ddl);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private Map<String, String> parseProperties(String ddl) {
        Map<String, String> properties = new LinkedHashMap<>();
        Matcher block = Pattern.compile("(?is)\\bPROPERTIES\\s*\\((.*?)\\)").matcher(ddl);
        if (!block.find()) {
            return properties;
        }
        Matcher property = PROPERTY_PATTERN.matcher(block.group(1));
        while (property.find()) {
            properties.put(property.group(1).toLowerCase(Locale.ROOT), property.group(2));
        }
        return properties;
    }

    private List<String> parseIdentifiers(String value) {
        List<String> identifiers = new ArrayList<>();
        Matcher matcher = QUOTED_IDENTIFIER_PATTERN.matcher(value == null ? "" : value);
        while (matcher.find()) {
            identifiers.add(matcher.group(1));
        }
        if (identifiers.isEmpty() && value != null) {
            for (String part : value.split(",")) {
                String name = part.trim().replace("`", "");
                if (!name.isEmpty()) {
                    identifiers.add(name);
                }
            }
        }
        return identifiers;
    }

    private Set<String> parseDistributionColumns(String clause) {
        Set<String> names = new LinkedHashSet<>();
        if (clause == null) {
            return names;
        }
        Matcher hash = Pattern.compile("(?is)HASH\\s*\\(([^)]*)\\)").matcher(clause);
        if (hash.find()) {
            parseIdentifiers(hash.group(1)).forEach(name -> names.add(name.toLowerCase(Locale.ROOT)));
        }
        return names;
    }

    private Set<String> parseIdentifiersFromClause(String clause) {
        Set<String> names = new LinkedHashSet<>();
        if (clause != null) {
            parseIdentifiers(clause).forEach(name -> names.add(name.toLowerCase(Locale.ROOT)));
        }
        return names;
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

    private String quoteIdentifier(String value) {
        return "`" + value.replace("`", "``") + "`";
    }
}
