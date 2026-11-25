package io.yak.ops.modeling.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.dao.entity.DataSource;
import io.yak.ops.dao.entity.DimensionDorisPublishPO;
import io.yak.ops.dao.entity.DimensionTableFieldPO;
import io.yak.ops.dao.entity.DimensionTablePO;
import io.yak.ops.dao.entity.VersionManagementPO;
import io.yak.ops.dao.entity.enums.DimensionDataTypeEnum;
import io.yak.ops.dao.entity.enums.DimensionTableTypeEnum;
import io.yak.ops.dao.entity.enums.DorisDataModelEnum;
import io.yak.ops.dao.entity.enums.DorisPublishModeEnum;
import io.yak.ops.dao.entity.enums.DorisPublishStatusEnum;
import io.yak.ops.dao.repository.modeling.DimensionDorisPublishDao;
import io.yak.ops.dao.repository.modeling.DimensionTableDao;
import io.yak.ops.dao.repository.modeling.DimensionTableFieldDao;
import io.yak.ops.dao.repository.modeling.VersionManagementDao;
import io.yak.ops.modeling.api.dto.dimension.DimensionDorisPublishDTO;
import io.yak.ops.modeling.api.vo.dimension.DimensionDorisFieldTypeOptionVO;
import io.yak.ops.modeling.api.vo.dimension.DimensionDorisPublishVO;
import io.yak.ops.modeling.application.service.DimensionDorisService;
import io.yak.ops.plugin.datasource.api.utils.DataSourceUtils;
import io.yak.ops.spi.datasource.ConnectionParam;
import io.yak.ops.spi.enums.DbType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.doris.flink.catalog.DorisTypeMapper;
import org.apache.doris.flink.catalog.doris.DataModel;
import org.apache.doris.flink.catalog.doris.DorisSchemaFactory;
import org.apache.doris.flink.catalog.doris.FieldSchema;
import org.apache.doris.flink.catalog.doris.TableSchema;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.types.DataType;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Doris physical-model workflow.
 *
 * <p>Business model metadata remains independent of a target datasource. This service uses the
 * same Doris SDK type mapper and schema factory as the Yak-OPS Flink CDC Doris connector.
 */
@Service
public class DimensionDorisServiceImpl implements DimensionDorisService {

    private static final String VERSION_TYPE = "DIMENSION_TABLE";
    private static final String IDENTIFIER_PATTERN = "^[A-Za-z][A-Za-z0-9_-]*$";
    @Resource private ObjectMapper objectMapper;
    @Resource private DimensionTableDao dimensionTableDao;
    @Resource private DimensionTableFieldDao dimensionTableFieldDao;
    @Resource private DimensionDorisPublishDao dimensionDorisPublishDao;
    @Resource private VersionManagementDao versionManagementDao;

    @Override
    public List<DimensionDorisFieldTypeOptionVO> getFieldTypeOptions() {
        List<DimensionDorisFieldTypeOptionVO> options = new ArrayList<>();
        options.add(option("TINYINT", "TINYINT", "NONE", null, null, null));
        options.add(option("SMALLINT", "SMALLINT", "NONE", null, null, null));
        options.add(option("INT", "INT", "NONE", null, null, null));
        options.add(option("BIGINT", "BIGINT", "NONE", null, null, null));
        options.add(option("BOOLEAN", "BOOLEAN", "NONE", null, null, null));
        options.add(option(
                "CHAR", "CHAR", "LENGTH", DorisTypeMapper.MAX_CHAR_SIZE / 3, null, null));
        options.add(option(
                "VARCHAR", "VARCHAR", "LENGTH", (DorisTypeMapper.MAX_VARCHAR_SIZE - 1) / 3,
                null, null));
        options.add(option("STRING", "STRING", "NONE", null, null, null));
        options.add(option("DECIMAL", "DECIMAL", "DECIMAL", null, 38, 38));
        options.add(option("FLOAT", "FLOAT", "NONE", null, null, null));
        options.add(option("DOUBLE", "DOUBLE", "NONE", null, null, null));
        options.add(option("DATE", "DATE", "NONE", null, null, null));
        options.add(option("TIME", "TIME（映射为 STRING）", "DATETIME_PRECISION", null, null, null));
        options.add(option(
                "DATETIME", "DATETIME", "DATETIME_PRECISION", null, null, null));
        options.add(option(
                "TIMESTAMP", "TIMESTAMP", "DATETIME_PRECISION", null, null, null));
        options.add(option("BINARY", "BINARY（映射为 STRING）", "NONE", null, null, null));
        options.add(option("VARBINARY", "VARBINARY（映射为 STRING）", "NONE", null, null, null));
        options.add(option("TEXT", "TEXT（映射为 STRING）", "NONE", null, null, null));
        options.add(option("JSON", "JSON（映射为 STRING）", "NONE", null, null, null));
        options.add(option("ARRAY", "ARRAY（映射为 STRING）", "NONE", null, null, null));
        options.add(option("MAP", "MAP（映射为 STRING）", "NONE", null, null, null));
        options.add(option("STRUCT", "STRUCT（映射为 STRING）", "NONE", null, null, null));
        return options;
    }

    @Override
    public DimensionDorisPublishVO preview(
            String tableId, DimensionDorisPublishDTO dto, DataSource datasource) {
        validatePublishRequest(dto, false);
        validateDatasource(datasource);
        String targetDatabase = resolveTargetDatabase(datasource);
        DimensionTablePO table = requirePhysicalTable(tableId);
        requireSubmitted(table);
        try (Connection connection = openDorisConnection(datasource)) {
            PublishPlan plan = buildPublishPlan(connection, tableId, targetDatabase, table, dto);
            DimensionDorisPublishVO vo = new DimensionDorisPublishVO();
            vo.setTableId(tableId);
            vo.setDatasourceId(datasource.getId());
            vo.setDatasourceName(datasource.getName());
            vo.setTargetDatabase(targetDatabase);
            vo.setTargetTable(table.getTableName());
            vo.setTableModel(dto.getTableModel());
            vo.setPublishMode(dto.getPublishMode());
            vo.setPublishRemark(StringUtils.trimToNull(dto.getPublishRemark()));
            vo.setDdl(plan.ddl);
            vo.setDdlHash(sha256(plan.ddl));
            vo.setNoChanges(plan.noChanges);
            return vo;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "连接 Doris 生成 DDL 预览失败: " + StringUtils.defaultIfBlank(ex.getMessage(), ex.getClass().getSimpleName()),
                    ex);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DimensionDorisPublishVO publish(
            String tableId, DimensionDorisPublishDTO dto, DataSource datasource, String operator) {
        validatePublishRequest(dto, true);
        validateDatasource(datasource);

        if (dto.getPublishMode() == DorisPublishModeEnum.DROP_RECREATE
                && !Boolean.TRUE.equals(dto.getConfirmDrop())) {
            throw new IllegalArgumentException("删除重建需要二次确认");
        }

        String targetDatabase = resolveTargetDatabase(datasource);
        DimensionTablePO table = lockPhysicalTable(tableId);
        requireSubmitted(table);
        VersionManagementPO version = latestVersion(tableId);

        DimensionDorisPublishPO publish = null;
        boolean recordCreated = false;
        long started = System.currentTimeMillis();
        String currentOperator = StringUtils.defaultIfBlank(operator, "sys");
        try (Connection connection = openDorisConnection(datasource)) {
            PublishPlan plan = buildPublishPlan(connection, tableId, targetDatabase, table, dto);

            publish = new DimensionDorisPublishPO();
            publish.setTableId(tableId);
            publish.setVersionId(version == null ? null : version.getId());
            publish.setVersionNumber(version == null ? null : version.getVersionNumber());
            publish.setDatasourceId(datasource.getId());
            publish.setDatasourceName(datasource.getName());
            publish.setTargetDatabase(targetDatabase);
            publish.setTargetTable(table.getTableName());
            publish.setTableModel(dto.getTableModel());
            publish.setPublishMode(dto.getPublishMode());
            publish.setPublishRemark(dto.getPublishRemark().trim());
            publish.setDdl(plan.ddl);
            publish.setDdlHash(sha256(plan.ddl));
            publish.setStatus(DorisPublishStatusEnum.PENDING);
            publish.setProjectCode(null);
            publish.initInsert();
            publish.setCreateBy(currentOperator);
            publish.setUpdateBy(currentOperator);
            if (dimensionDorisPublishDao.insert(publish) <= 0) {
                publish = null;
                throw new IllegalArgumentException("创建 Doris 发布记录失败");
            }
            recordCreated = true;

            publish.setStatus(DorisPublishStatusEnum.RUNNING);
            publish.setStartedAt(new Date(started));
            publish.initUpdate();
            publish.setUpdateBy(currentOperator);
            dimensionDorisPublishDao.updateById(publish);

            for (String statementSql : plan.statements) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(statementSql);
                }
            }

            if (!tableMatchesModel(connection, targetDatabase, table.getTableName(), tableId)) {
                throw new IllegalStateException("DDL 执行后目标 Doris 表结构与当前模型不一致，请检查目标库中的表定义。");
            }
            publish.setStatus(DorisPublishStatusEnum.SUCCESS);
        } catch (Exception ex) {
            if (!recordCreated) {
                if (ex instanceof RuntimeException) {
                    throw (RuntimeException) ex;
                }
                throw new IllegalArgumentException(
                        "Doris 发布失败: " + StringUtils.defaultIfBlank(ex.getMessage(), ex.getClass().getSimpleName()),
                        ex);
            }
            publish.setStatus(DorisPublishStatusEnum.FAILED);
            publish.setErrorMessage(StringUtils.left(ex.getMessage(), 4000));
        } finally {
            if (recordCreated) {
                publish.setFinishedAt(new Date());
                publish.setExecutionDurationMs(System.currentTimeMillis() - started);
                publish.initUpdate();
                publish.setUpdateBy(currentOperator);
                dimensionDorisPublishDao.updateById(publish);
            }
        }

        DimensionDorisPublishPO saved = dimensionDorisPublishDao.queryById(publish.getId());
        return toPublishVO(saved == null ? publish : saved);
    }

    @Override
    public List<DimensionDorisPublishVO> history(String tableId) {
        requirePhysicalTable(tableId);
        List<DimensionDorisPublishPO> records = dimensionDorisPublishDao.queryList(
                new QueryWrapper<DimensionDorisPublishPO>()
                        .eq("table_id", tableId).orderByDesc("create_time"));
        return safeList(records).stream().map(this::toPublishVO).collect(Collectors.toList());
    }

    private PublishPlan buildPublishPlan(
            Connection connection,
            String tableId,
            String targetDatabase,
            DimensionTablePO table,
            DimensionDorisPublishDTO dto) throws Exception {
        String createDdl = buildCreateDdl(tableId, targetDatabase, table, dto.getTableModel());
        String qualifiedTable = quoteIdentifier(targetDatabase) + "." + quoteIdentifier(table.getTableName());
        List<String> statements = new ArrayList<>();

        if (dto.getPublishMode() == DorisPublishModeEnum.DROP_RECREATE) {
            statements.add("DROP TABLE IF EXISTS " + qualifiedTable);
            statements.add(createDdl);
            return new PublishPlan(joinStatements(statements), statements, false);
        }

        if (!tableExists(connection, targetDatabase, table.getTableName())) {
            statements.add(createDdl);
            return new PublishPlan(joinStatements(statements), statements, false);
        }

        DimensionDorisPublishPO previous = latestSuccessfulPublish(
                tableId, dto.getDatasourceId(), targetDatabase, table.getTableName());
        if (previous == null) {
            throw new IllegalArgumentException(
                    "目标 Doris 表已存在，但没有 Yak-OPS 成功发布记录。为保证增量发布安全，请选择“删除重建”。");
        }
        if (previous.getTableModel() != null && previous.getTableModel() != dto.getTableModel()) {
            throw new IllegalArgumentException("目标表模型与本次选择不一致，请使用“删除重建”切换表模型。");
        }
        String previousKey = extractKeyDefinition(previous.getDdl());
        String currentKey = extractKeyDefinition(createDdl);
        if (previousKey == null || currentKey == null || !previousKey.equals(currentKey)) {
            throw new IllegalArgumentException("目标表 Key 定义已变化，增量发布不支持修改 Key 字段，请使用“删除重建”。");
        }

        List<DimensionTableFieldPO> fields = listFields(tableId);
        Map<String, DimensionTableFieldPO> expectedFields = new LinkedHashMap<>();
        for (DimensionTableFieldPO field : fields) {
            expectedFields.put(field.getFieldName().toLowerCase(Locale.ROOT), field);
        }
        Map<String, ColumnMetadata> actualColumns =
                readActualColumns(connection, targetDatabase, table.getTableName());
        if (actualColumns.isEmpty()) {
            throw new IllegalArgumentException("无法读取目标 Doris 表结构，增量发布已停止，请检查数据源权限或使用删除重建。");
        }

        for (String actualName : actualColumns.keySet()) {
            if (!expectedFields.containsKey(actualName)) {
                throw new IllegalArgumentException(
                        "目标表存在模型中已删除或未定义的字段: " + actualName
                                + "。增量发布不删除字段，请使用“删除重建”。");
            }
        }

        for (Map.Entry<String, DimensionTableFieldPO> expected : expectedFields.entrySet()) {
            ColumnMetadata actual = actualColumns.get(expected.getKey());
            DimensionTableFieldPO field = expected.getValue();
            if (actual == null) {
                if (Boolean.FALSE.equals(field.getCanNull())) {
                    throw new IllegalArgumentException(
                            "增量发布暂不支持新增非空字段，请将字段设为可空后重试: " + field.getFieldName());
                }
                statements.add(buildAddColumnDdl(targetDatabase, table.getTableName(), field));
            } else if (!columnMatchesField(actual, field)) {
                throw new IllegalArgumentException(
                        "字段结构发生不兼容变化: " + field.getFieldName()
                                + "。增量发布不修改字段类型、长度或精度，请使用“删除重建”。");
            }
        }

        if (statements.isEmpty()) {
            return new PublishPlan("-- 当前目标表结构与模型一致，无需执行变更。", statements, true);
        }
        return new PublishPlan(joinStatements(statements), statements, false);
    }

    private String buildCreateDdl(
            String tableId, String targetDatabase, DimensionTablePO table, DorisDataModelEnum tableModel) {
        if (!isIdentifier(targetDatabase)) {
            throw new IllegalArgumentException("目标 Doris Database 名称不合法");
        }
        if (!isIdentifier(table.getTableName())) {
            throw new IllegalArgumentException("模型英文表名不符合 Doris 标识符规则");
        }

        List<DimensionTableFieldPO> fields = listFields(tableId);
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("物理建表至少需要一个字段");
        }

        Set<String> fieldNames = new HashSet<>();
        List<DimensionTableFieldPO> primaryFields = new ArrayList<>();
        for (DimensionTableFieldPO field : fields) {
            if (!isIdentifier(field.getFieldName())) {
                throw new IllegalArgumentException("字段英文名称不合法: " + field.getFieldName());
            }
            if (!fieldNames.add(field.getFieldName().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("字段英文名称重复: " + field.getFieldName());
            }
            if (Boolean.TRUE.equals(field.getPrimaryFlag())) {
                primaryFields.add(field);
            }
        }

        if (tableModel == DorisDataModelEnum.UNIQUE && primaryFields.isEmpty()) {
            throw new IllegalArgumentException("UNIQUE KEY 主键表至少需要一个主键字段，请先在字段管理中设置主键。");
        }
        if (tableModel == DorisDataModelEnum.UNIQUE) {
            for (DimensionTableFieldPO field : primaryFields) {
                if (!Boolean.FALSE.equals(field.getCanNull())) {
                    throw new IllegalArgumentException(
                            "UNIQUE KEY 主键字段必须设置为非空，请检查字段: " + field.getFieldName());
                }
            }
        }

        List<DimensionTableFieldPO> keyFields = primaryFields.isEmpty()
                ? Collections.singletonList(fields.get(0))
                : primaryFields;
        List<String> keyNames = keyFields.stream()
                .map(DimensionTableFieldPO::getFieldName)
                .collect(Collectors.toList());
        String distributionField = keyNames.get(0);

        Map<String, FieldSchema> fieldSchemas = new LinkedHashMap<>();
        for (DimensionTableFieldPO field : fields) {
            String dorisType = DorisTypeMapper.toDorisType(toFlinkType(field));
            boolean keyField = keyNames.contains(field.getFieldName());
            if (Boolean.FALSE.equals(field.getCanNull()) && !keyField) {
                throw new IllegalArgumentException(
                        "当前 Doris SDK DDL Builder 无法表达普通 Value 字段 NOT NULL，请将字段设为可空: "
                                + field.getFieldName());
            }
            validateDefaultValue(field.getDefaultValue(), field.getFieldName());
            String comment = StringUtils.defaultIfBlank(field.getRemark(), field.getCnName());
            fieldSchemas.put(
                    field.getFieldName(),
                    new FieldSchema(field.getFieldName(), dorisType, field.getDefaultValue(), comment));
        }

        TableSchema schema = new TableSchema();
        schema.setDatabase(targetDatabase);
        schema.setTable(table.getTableName());
        schema.setTableComment(StringUtils.defaultIfBlank(table.getCnName(), table.getRemark()));
        schema.setFields(fieldSchemas);
        schema.setModel(DataModel.valueOf(tableModel.name()));
        schema.setKeys(keyNames);
        schema.setDistributeKeys(Collections.singletonList(distributionField));
        schema.setProperties(Collections.emptyMap());
        schema.setTableBuckets(null);

        String generatedDdl = DorisSchemaFactory.generateCreateTableDDL(schema);
        return generatedDdl.replaceFirst(
                "(?i)^CREATE TABLE IF NOT EXISTS\\s+", "CREATE TABLE ");
    }

    private DimensionDorisPublishPO latestSuccessfulPublish(
            String tableId, Integer datasourceId, String database, String table) {
        List<DimensionDorisPublishPO> records = dimensionDorisPublishDao.queryList(
                new QueryWrapper<DimensionDorisPublishPO>()
                        .eq("table_id", tableId)
                        .eq("datasource_id", datasourceId)
                        .eq("target_database", database)
                        .eq("target_table", table)
                        .eq("status", DorisPublishStatusEnum.SUCCESS)
                        .orderByDesc("create_time"));
        return safeList(records).stream().findFirst().orElse(null);
    }

    private String extractKeyDefinition(String ddl) {
        if (StringUtils.isBlank(ddl)) {
            return null;
        }
        Matcher matcher = Pattern.compile("(?is)\\b(UNIQUE|DUPLICATE)\\s+KEY\\s*\\(([^)]*)\\)")
                .matcher(ddl);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1).toUpperCase(Locale.ROOT) + ":"
                + matcher.group(2).replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private Map<String, ColumnMetadata> readActualColumns(
            Connection connection, String database, String table) throws Exception {
        DatabaseMetaData metadata = connection.getMetaData();
        Map<String, ColumnMetadata> columnsByName = new LinkedHashMap<>();
        try (ResultSet columns = metadata.getColumns(
                database, null, metadataPattern(metadata, table), null)) {
            while (columns.next()) {
                if (!table.equalsIgnoreCase(columns.getString("TABLE_NAME"))) {
                    continue;
                }
                String name = columns.getString("COLUMN_NAME");
                String type = columns.getString("TYPE_NAME");
                if (StringUtils.isBlank(name) || StringUtils.isBlank(type)) {
                    continue;
                }
                int sizeValue = columns.getInt("COLUMN_SIZE");
                Integer size = columns.wasNull() ? null : sizeValue;
                int scaleValue = columns.getInt("DECIMAL_DIGITS");
                Integer scale = columns.wasNull() ? null : scaleValue;
                columnsByName.put(
                        name.toLowerCase(Locale.ROOT),
                        new ColumnMetadata(type, size, scale));
            }
        }
        return columnsByName;
    }

    private boolean columnMatchesField(ColumnMetadata actual, DimensionTableFieldPO field) {
        String expectedType = DorisTypeMapper.toDorisType(toFlinkType(field));
        String expectedBase = normalizeDorisTypeName(expectedType);
        if (!expectedBase.equals(normalizeDorisTypeName(actual.typeName))) {
            return false;
        }
        Matcher parameters = Pattern.compile("\\((\\d+)(?:,(\\d+))?\\)").matcher(expectedType);
        if (!parameters.find()) {
            return true;
        }
        if ("CHAR".equals(expectedBase) || "VARCHAR".equals(expectedBase) || "DECIMAL".equals(expectedBase)) {
            Integer expectedSize = Integer.valueOf(parameters.group(1));
            if (actual.size == null || !expectedSize.equals(actual.size)) {
                return false;
            }
        }
        if ("DECIMAL".equals(expectedBase) && parameters.group(2) != null) {
            Integer expectedScale = Integer.valueOf(parameters.group(2));
            return actual.scale != null && expectedScale.equals(actual.scale);
        }
        return true;
    }

    private String buildAddColumnDdl(String database, String table, DimensionTableFieldPO field) {
        String type = DorisTypeMapper.toDorisType(toFlinkType(field));
        StringBuilder ddl = new StringBuilder("ALTER TABLE ")
                .append(quoteIdentifier(database)).append(".").append(quoteIdentifier(table))
                .append(" ADD COLUMN ").append(quoteIdentifier(field.getFieldName()))
                .append(" ").append(type).append(" NULL");

        String defaultValue = StringUtils.trimToNull(field.getDefaultValue());
        if (defaultValue != null) {
            validateDefaultValue(defaultValue, field.getFieldName());
            ddl.append(" DEFAULT ").append(formatDefaultValue(defaultValue, field.getDataType()));
        }
        String comment = StringUtils.defaultIfBlank(field.getRemark(), field.getCnName());
        if (StringUtils.isNotBlank(comment)) {
            ddl.append(" COMMENT '").append(escapeSqlLiteral(comment)).append("'");
        }
        return ddl.toString();
    }

    private String formatDefaultValue(String value, DimensionDataTypeEnum type) {
        switch (type) {
            case TINYINT:
            case SMALLINT:
            case INT:
            case BIGINT:
            case DECIMAL:
            case FLOAT:
            case DOUBLE:
                if (!value.matches("[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][-+]?\\d+)?")) {
                    throw new IllegalArgumentException("数值字段默认值格式不合法: " + value);
                }
                return value;
            case BOOLEAN:
                if (!value.matches("(?i)TRUE|FALSE")) {
                    throw new IllegalArgumentException("BOOLEAN 字段默认值格式不合法: " + value);
                }
                return value.toUpperCase(Locale.ROOT);
            case DATE:
            case TIME:
            case DATETIME:
            case TIMESTAMP:
                if (value.matches("(?i)CURRENT_TIMESTAMP(?:\\(\\))?")) {
                    return value;
                }
                return "'" + escapeSqlLiteral(value) + "'";
            default:
                return "'" + escapeSqlLiteral(value) + "'";
        }
    }

    private String escapeSqlLiteral(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    private String quoteIdentifier(String value) {
        if (!isIdentifier(value)) {
            throw new IllegalArgumentException("Doris 标识符不合法: " + value);
        }
        return "`" + value + "`";
    }

    private String joinStatements(List<String> statements) {
        return statements.stream().collect(Collectors.joining(";\n\n")) + ";";
    }

    private String resolveTargetDatabase(DataSource datasource) {
        if (StringUtils.isBlank(datasource.getConnectionParams())) {
            throw new IllegalArgumentException("Doris 数据源未配置数据库，请先在数据源管理中配置数据库");
        }
        try {
            JsonNode connectionParams = objectMapper.readTree(datasource.getConnectionParams());
            JsonNode databaseNode = connectionParams == null ? null : connectionParams.get("database");
            String targetDatabase = databaseNode != null && databaseNode.isTextual()
                    ? databaseNode.asText()
                    : "";
            if (StringUtils.isBlank(targetDatabase)) {
                throw new IllegalArgumentException("Doris 数据源未配置数据库，请先在数据源管理中配置数据库");
            }
            return targetDatabase.trim();
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("无法解析 Doris 数据源的数据库配置", ex);
        }
    }

    private void validatePublishRequest(DimensionDorisPublishDTO dto, boolean publishing) {
        if (dto == null) {
            throw new IllegalArgumentException("Doris 发布参数不能为空");
        }
        if (dto.getDatasourceId() == null) {
            throw new IllegalArgumentException("请选择 Doris 数据源");
        }
        if (dto.getTableModel() == null) {
            throw new IllegalArgumentException("请选择 Doris 表类型");
        }
        if (dto.getPublishMode() == null) {
            throw new IllegalArgumentException("请选择发布模式");
        }
        if (publishing && StringUtils.isBlank(dto.getPublishRemark())) {
            throw new IllegalArgumentException("请输入发布备注");
        }
        if (StringUtils.length(dto.getPublishRemark()) > 2048) {
            throw new IllegalArgumentException("发布备注不能超过 2048 个字符");
        }
    }

    private void requireSubmitted(DimensionTablePO table) {
        if (!Boolean.TRUE.equals(table.getSubmit())) {
            throw new IllegalArgumentException("请先提交维度模型，再预览或发布 Doris 建表");
        }
    }

    private static class PublishPlan {
        private final String ddl;
        private final List<String> statements;
        private final boolean noChanges;

        private PublishPlan(String ddl, List<String> statements, boolean noChanges) {
            this.ddl = ddl;
            this.statements = statements;
            this.noChanges = noChanges;
        }
    }

    private static class ColumnMetadata {
        private final String typeName;
        private final Integer size;
        private final Integer scale;

        private ColumnMetadata(String typeName, Integer size, Integer scale) {
            this.typeName = typeName;
            this.size = size;
            this.scale = scale;
        }
    }



    private DataType toFlinkType(DimensionTableFieldPO field) {
        DimensionDataTypeEnum type = field.getDataType();
        if (type == null) {
            throw new IllegalArgumentException("字段类型不能为空: " + field.getFieldName());
        }
        switch (type) {
            case TINYINT:
                return DataTypes.TINYINT();
            case SMALLINT:
                return DataTypes.SMALLINT();
            case INT:
                return DataTypes.INT();
            case BIGINT:
                return DataTypes.BIGINT();
            case BOOLEAN:
                return DataTypes.BOOLEAN();
            case CHAR:
                validateLength(field, 1, DorisTypeMapper.MAX_CHAR_SIZE / 3);
                return DataTypes.CHAR(field.getLength());
            case VARCHAR:
                validateLength(field, 1, (DorisTypeMapper.MAX_VARCHAR_SIZE - 1) / 3);
                return DataTypes.VARCHAR(field.getLength());
            case DECIMAL:
                Integer precision = field.getFieldPrecision();
                Integer scale = field.getFieldScale() == null ? 0 : field.getFieldScale();
                if (precision == null || precision < 1 || precision > 38) {
                    throw new IllegalArgumentException(
                            "DECIMAL 精度必须在 1 到 38 之间: " + field.getFieldName());
                }
                if (scale < 0 || scale > precision) {
                    throw new IllegalArgumentException(
                            "DECIMAL 小数位数必须在 0 到精度之间: " + field.getFieldName());
                }
                return DataTypes.DECIMAL(precision, scale);
            case FLOAT:
                return DataTypes.FLOAT();
            case DOUBLE:
                return DataTypes.DOUBLE();
            case DATE:
                return DataTypes.DATE();
            case TIME:
                return DataTypes.TIME(normalizeDatetimePrecision(field));
            case DATETIME:
            case TIMESTAMP:
                return DataTypes.TIMESTAMP(normalizeDatetimePrecision(field));
            case STRING:
            case TEXT:
            case BINARY:
            case VARBINARY:
            case JSON:
            case ARRAY:
            case MAP:
            case STRUCT:
                return DataTypes.STRING();
            default:
                throw new IllegalArgumentException("当前 Doris 类型映射暂不支持: " + type);
        }
    }

    private int normalizeDatetimePrecision(DimensionTableFieldPO field) {
        Integer precision = field.getDatetimePrecision();
        if (precision == null) {
            return 0;
        }
        if (precision < 0 || precision > 6) {
            throw new IllegalArgumentException(
                    "Doris 时间精度必须在 0 到 6 之间: " + field.getFieldName());
        }
        return precision;
    }

    private void validateLength(DimensionTableFieldPO field, int min, int max) {
        if (field.getLength() == null || field.getLength() < min || field.getLength() > max) {
            throw new IllegalArgumentException(
                    field.getDataType() + " 长度必须在 " + min + " 到 " + max + " 之间: "
                            + field.getFieldName());
        }
    }

    private void validateDefaultValue(String value, String fieldName) {
        if (value == null) {
            return;
        }
        if (value.length() > 255 || value.indexOf('\'') >= 0 || value.indexOf('\\') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(
                    "默认值包含当前 Doris DDL Builder 不支持的字符: " + fieldName);
        }
    }

    private List<DimensionTableFieldPO> listFields(String tableId) {
        List<DimensionTableFieldPO> fields = dimensionTableFieldDao.queryList(
                new QueryWrapper<DimensionTableFieldPO>()
                        .eq("table_id", tableId).orderByAsc("ordinal").orderByAsc("create_time"));
        return safeList(fields);
    }

    private DimensionDorisFieldTypeOptionVO option(
            String code, String label, String parameterType,
            Integer maxLength, Integer maxPrecision, Integer maxScale) {
        return new DimensionDorisFieldTypeOptionVO(
                code, label, parameterType, maxLength, maxPrecision, maxScale);
    }

    private DimensionTablePO requireTable(String tableId) {
        DimensionTablePO table = dimensionTableDao.queryById(tableId);
        if (table == null) {
            throw new IllegalArgumentException("维度建模记录不存在或不属于当前项目");
        }
        return table;
    }

    private DimensionTablePO requirePhysicalTable(String tableId) {
        DimensionTablePO table = requireTable(tableId);
        validatePhysicalTable(table);
        return table;
    }

    private DimensionTablePO lockPhysicalTable(String tableId) {
        if (StringUtils.isBlank(tableId)) {
            throw new IllegalArgumentException("维度表ID不能为空");
        }
        DimensionTablePO table = dimensionTableDao.queryOne(
                new QueryWrapper<DimensionTablePO>()
                        .eq("id", tableId)
                        .last("FOR UPDATE"));
        if (table == null) {
            throw new IllegalArgumentException("维度建模记录不存在或不属于当前项目");
        }
        validatePhysicalTable(table);
        return table;
    }

    private void validatePhysicalTable(DimensionTablePO table) {
        if (table.getTableType() == DimensionTableTypeEnum.DIM) {
            throw new IllegalArgumentException("DIM 是逻辑维度定义，不生成 Doris 物理表");
        }
        if (table.getTableType() == null) {
            throw new IllegalArgumentException("模型类型不能为空");
        }
    }

    private void validateDatasource(DataSource datasource) {
        if (datasource == null || datasource.getType() != DbType.DORIS) {
            throw new IllegalArgumentException("物理建表发布目前仅支持 Doris 数据源");
        }
    }

    Connection openDorisConnection(DataSource datasource) throws Exception {
        ConnectionParam connectionParam = DataSourceUtils.buildConnectionParams(
                DbType.DORIS, datasource.getConnectionParams());
        return DataSourceUtils.getConnection(DbType.DORIS, connectionParam);
    }

    private boolean tableExists(Connection connection, String database, String table)
            throws Exception {
        DatabaseMetaData metadata = connection.getMetaData();
        String tablePattern = metadataPattern(metadata, table);
        try (ResultSet result =
                metadata.getTables(database, null, tablePattern, new String[] {"TABLE"})) {
            if (hasExactTable(result, table)) {
                return true;
            }
        }

        String upperTable = table.toUpperCase(Locale.ROOT);
        try (ResultSet result = metadata.getTables(
                database.toUpperCase(Locale.ROOT),
                null,
                metadataPattern(metadata, upperTable),
                new String[] {"TABLE"})) {
            return hasExactTable(result, table);
        }
    }

    private boolean hasExactTable(ResultSet tables, String expectedTable) throws Exception {
        while (tables.next()) {
            String actualTable = tables.getString("TABLE_NAME");
            if (expectedTable.equalsIgnoreCase(actualTable)) {
                return true;
            }
        }
        return false;
    }

    private String metadataPattern(DatabaseMetaData metadata, String identifier) throws Exception {
        String escape = metadata.getSearchStringEscape();
        if (StringUtils.isBlank(escape)) {
            // Exact comparison is still applied to every metadata row below. A null escape
            // means the driver cannot safely express a literal underscore or percent pattern.
            return identifier;
        }
        return identifier
                .replace(escape, escape + escape)
                .replace("%", escape + "%")
                .replace("_", escape + "_");
    }

    private boolean tableMatchesModel(
            Connection connection, String database, String table, String tableId) throws Exception {
        List<DimensionTableFieldPO> fields = listFields(tableId);
        Map<String, ColumnMetadata> actualColumns = readActualColumns(connection, database, table);
        if (fields.isEmpty() || fields.size() != actualColumns.size()) {
            return false;
        }
        for (DimensionTableFieldPO field : fields) {
            ColumnMetadata actual = actualColumns.get(field.getFieldName().toLowerCase(Locale.ROOT));
            if (actual == null || !columnMatchesField(actual, field)) {
                return false;
            }
        }
        return true;
    }



    private String normalizeDorisTypeName(String typeName) {
        int parameterStart = typeName.indexOf('(');
        String baseType = (parameterStart >= 0
                        ? typeName.substring(0, parameterStart)
                        : typeName)
                .trim()
                .toUpperCase(Locale.ROOT);
        if ("DECIMALV3".equals(baseType)) {
            return "DECIMAL";
        }
        if ("DATETIMEV2".equals(baseType)) {
            return "DATETIME";
        }
        if ("DATEV2".equals(baseType)) {
            return "DATE";
        }
        return baseType;
    }

    private VersionManagementPO latestVersion(String tableId) {
        List<VersionManagementPO> versions = versionManagementDao.queryList(
                new QueryWrapper<VersionManagementPO>()
                        .eq("source_table_id", tableId)
                        .eq("version_type", VERSION_TYPE)
                        .orderByDesc("version_number"));
        return versions == null || versions.isEmpty() ? null : versions.get(0);
    }

    private DimensionDorisPublishVO toPublishVO(DimensionDorisPublishPO po) {
        if (po == null) {
            return null;
        }
        DimensionDorisPublishVO vo = new DimensionDorisPublishVO();
        BeanUtils.copyProperties(po, vo);
        return vo;
    }

    private boolean isIdentifier(String value) {
        return value != null && value.matches(IDENTIFIER_PATTERN);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte item : digest) {
                hex.append(String.format("%02x", item));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("计算 DDL 指纹失败", ex);
        }
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }
}
