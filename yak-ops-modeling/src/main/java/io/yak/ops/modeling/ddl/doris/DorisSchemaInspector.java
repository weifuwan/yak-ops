package io.yak.ops.modeling.ddl.doris;

import io.yak.ops.dao.entity.enums.DorisDataModelEnum;
import io.yak.ops.modeling.ddl.model.DdlColumnDefinition;
import io.yak.ops.modeling.ddl.model.DdlTableDefinition;
import io.yak.ops.modeling.ddl.model.DorisColumnMetadata;
import io.yak.ops.modeling.ddl.model.DorisPhysicalTableMetadata;
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
public class DorisSchemaInspector {

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

    public Map<String, DorisColumnMetadata> readColumns(
            Connection connection, DataSourceCatalog catalog, String database, String tableName) {
        List<DataSourceCatalogColumn> columns = catalog.listColumns(
                connection, new CatalogPath(null, database, null, tableName, CatalogObjectType.TABLE));
        Map<String, DorisColumnMetadata> result = new LinkedHashMap<>();
        if (columns == null) {
            return result;
        }
        for (DataSourceCatalogColumn column : columns) {
            if (column == null || column.getName() == null || column.getName().trim().isEmpty()) {
                continue;
            }
            result.put(column.getName().toLowerCase(Locale.ROOT), new DorisColumnMetadata(
                    column.getName(),
                    column.getTypeName(),
                    column.getSize(),
                    column.getScale(),
                    column.getNullable(),
                    column.isPrimaryKey()));
        }
        return result;
    }

    public DorisPhysicalTableMetadata inspect(
            Connection connection, DataSourceCatalog catalog, String database, String tableName) {
        if (!tableExists(connection, catalog, database, tableName)) {
            return null;
        }
        String createDdl = readCreateTableDdl(connection, database, tableName);
        Map<String, DorisColumnMetadata> columns = readColumns(connection, catalog, database, tableName);
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
        DorisDataModelEnum tableModel = DorisDataModelEnum.valueOf(modelName);
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

        return new DorisPhysicalTableMetadata(
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

    public boolean matches(
            Connection connection,
            DataSourceCatalog catalog,
            DdlTableDefinition table,
            DorisDataModelEnum expectedModel,
            List<String> expectedKeyFields,
            DorisDdlGenerator generator) {
        DorisPhysicalTableMetadata actual = inspect(
                connection, catalog, table.getDatabase(), table.getTableName());
        if (actual == null || actual.getTableModel() != expectedModel
                || !sameIdentifiers(actual.getKeyFields(), expectedKeyFields)) {
            return false;
        }
        if (table.getColumns().isEmpty() || table.getColumns().size() != actual.getColumns().size()) {
            return false;
        }
        for (DdlColumnDefinition expected : table.getColumns()) {
            DorisColumnMetadata actualColumn =
                    actual.getColumns().get(expected.getName().toLowerCase(Locale.ROOT));
            if (actualColumn == null || !generator.matches(actualColumn, expected)) {
                return false;
            }
        }
        return true;
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
