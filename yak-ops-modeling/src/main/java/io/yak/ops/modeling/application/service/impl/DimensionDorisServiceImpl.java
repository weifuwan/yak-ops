package io.yak.ops.modeling.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.dao.entity.DataSource;
import io.yak.ops.dao.entity.DimensionDorisPublishPO;
import io.yak.ops.dao.entity.DimensionTableFieldPO;
import io.yak.ops.dao.entity.DimensionTablePO;
import io.yak.ops.dao.entity.VersionManagementPO;
import io.yak.ops.dao.entity.enums.DimensionDataTypeEnum;
import io.yak.ops.dao.entity.enums.DimensionTableTypeEnum;
import io.yak.ops.dao.entity.enums.DorisBucketModeEnum;
import io.yak.ops.dao.entity.enums.DorisDataModelEnum;
import io.yak.ops.dao.entity.enums.DorisDistributionTypeEnum;
import io.yak.ops.dao.entity.enums.DorisPartitionStrategyEnum;
import io.yak.ops.dao.entity.enums.DorisPartitionUnitEnum;
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
import java.sql.SQLException;
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
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.doris.flink.catalog.DorisTypeMapper;
import org.apache.doris.flink.catalog.doris.DataModel;
import org.apache.doris.flink.catalog.doris.DorisSchemaFactory;
import org.apache.doris.flink.catalog.doris.FieldSchema;
import org.apache.doris.flink.catalog.doris.TableSchema;
import org.apache.flink.api.java.tuple.Tuple2;
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
        validatePublishRequest(dto);
        validateDatasource(datasource);
        DimensionTablePO table = requirePhysicalTable(tableId);
        requireSubmitted(table);
        BuildResult build = buildDdl(tableId, dto);
        DimensionDorisPublishVO vo = new DimensionDorisPublishVO();
        vo.setTableId(tableId);
        vo.setDatasourceId(datasource.getId());
        vo.setDatasourceName(datasource.getName());
        vo.setTargetDatabase(dto.getTargetDatabase().trim());
        vo.setTargetTable(table.getTableName());
        vo.setDdl(build.ddl);
        vo.setDdlHash(sha256(build.ddl));
        vo.setPhysicalConfig(build.physicalConfig);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DimensionDorisPublishVO publish(
            String tableId, DimensionDorisPublishDTO dto, DataSource datasource, String operator) {
        validatePublishRequest(dto);
        validateDatasource(datasource);

        // Serialize publishes for one model across application instances. The model-row lock is
        // held until the external DDL and the publish-history update have both completed.
        DimensionTablePO table = lockPhysicalTable(tableId);
        requireSubmitted(table);

        BuildResult build = buildDdl(tableId, dto);
        String ddl = build.ddl;
        String ddlHash = sha256(ddl);
        VersionManagementPO version = latestVersion(tableId);
        List<DimensionDorisPublishPO> previousRecords = dimensionDorisPublishDao.queryList(
                new QueryWrapper<DimensionDorisPublishPO>()
                        .eq("table_id", tableId)
                        .eq("datasource_id", datasource.getId())
                        .eq("target_database", dto.getTargetDatabase().trim())
                        .eq("target_table", table.getTableName())
                        .eq("ddl_hash", ddlHash)
                        .eq("status", DorisPublishStatusEnum.SUCCESS)
                        .orderByDesc("create_time"));
        DimensionDorisPublishPO previous = safeList(previousRecords).stream()
                .findFirst()
                .orElse(null);

        DimensionDorisPublishPO publish = new DimensionDorisPublishPO();
        publish.setTableId(tableId);
        publish.setVersionId(version == null ? null : version.getId());
        publish.setVersionNumber(version == null ? null : version.getVersionNumber());
        publish.setDatasourceId(datasource.getId());
        publish.setDatasourceName(datasource.getName());
        publish.setTargetDatabase(dto.getTargetDatabase().trim());
        publish.setTargetTable(table.getTableName());
        publish.setPhysicalConfig(build.physicalConfig);
        publish.setDdl(ddl);
        publish.setDdlHash(ddlHash);
        publish.setStatus(DorisPublishStatusEnum.PENDING);
        publish.setProjectCode(null);
        publish.initInsert();
        publish.setCreateBy(StringUtils.defaultIfBlank(operator, "sys"));
        publish.setUpdateBy(StringUtils.defaultIfBlank(operator, "sys"));
        if (dimensionDorisPublishDao.insert(publish) <= 0) {
            throw new IllegalArgumentException("创建 Doris 发布记录失败");
        }

        long started = System.currentTimeMillis();
        publish.setStatus(DorisPublishStatusEnum.RUNNING);
        publish.setStartedAt(new Date(started));
        publish.initUpdate();
        publish.setUpdateBy(StringUtils.defaultIfBlank(operator, "sys"));
        dimensionDorisPublishDao.updateById(publish);

        try (Connection connection = openDorisConnection(datasource)) {
            boolean targetExists =
                    tableExists(connection, dto.getTargetDatabase().trim(), table.getTableName());
            if (targetExists) {
                if (previous != null
                        && tableMatchesDdl(
                                connection, dto.getTargetDatabase().trim(), table.getTableName(), tableId)) {
                    publish.setStatus(DorisPublishStatusEnum.SUCCESS);
                } else {
                    publish.setStatus(DorisPublishStatusEnum.CONFLICT);
                    publish.setErrorMessage(
                            previous == null
                                    ? "目标 Doris 表已存在。为避免覆盖现有数据，本次发布未执行 DDL。"
                                    : "目标 Doris 表结构与当前模型不一致。为避免覆盖现有数据，本次发布未执行 DDL。");
                }
            } else {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(ddl);
                } catch (SQLException ex) {
                    if (tableExists(connection, dto.getTargetDatabase().trim(), table.getTableName())) {
                        publish.setStatus(DorisPublishStatusEnum.CONFLICT);
                        publish.setErrorMessage(
                                "目标 Doris 表在发布过程中已被创建，本次发布未覆盖现有表。");
                    } else {
                        throw ex;
                    }
                }
                if (publish.getStatus() != DorisPublishStatusEnum.CONFLICT) {
                    if (tableMatchesDdl(
                            connection, dto.getTargetDatabase().trim(), table.getTableName(), tableId)) {
                        publish.setStatus(DorisPublishStatusEnum.SUCCESS);
                    } else {
                        publish.setStatus(DorisPublishStatusEnum.CONFLICT);
                        publish.setErrorMessage(
                                "DDL 执行后目标 Doris 表结构与当前模型不一致，请检查目标库中的表定义。");
                    }
                }
            }
        } catch (Exception ex) {
            publish.setStatus(DorisPublishStatusEnum.FAILED);
            publish.setErrorMessage(StringUtils.left(ex.getMessage(), 4000));
        } finally {
            publish.setFinishedAt(new Date());
            publish.setExecutionDurationMs(System.currentTimeMillis() - started);
            publish.initUpdate();
            publish.setUpdateBy(StringUtils.defaultIfBlank(operator, "sys"));
            dimensionDorisPublishDao.updateById(publish);
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

    private BuildResult buildDdl(String tableId, DimensionDorisPublishDTO dto) {
        DimensionTablePO table = requirePhysicalTable(tableId);
        String targetDatabase = dto.getTargetDatabase().trim();
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
        Map<String, DimensionTableFieldPO> fieldsById = new HashMap<>();
        Set<String> fieldNames = new HashSet<>();
        for (DimensionTableFieldPO field : fields) {
            if (!isIdentifier(field.getFieldName())) {
                throw new IllegalArgumentException("字段英文名称不合法: " + field.getFieldName());
            }
            if (!fieldNames.add(field.getFieldName().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("字段英文名称重复: " + field.getFieldName());
            }
            fieldsById.put(field.getId(), field);
        }

        ResolvedDorisConfig config = resolvePhysicalConfig(fields, fieldsById, dto.getAdvanced());
        List<String> schemaKeyIds = new ArrayList<>(config.keyFieldIds);
        if (config.partitionFieldId != null) {
            // Doris AUTO RANGE partition columns are included in the physical UNIQUE KEY by
            // the connector's schema factory. Do not pass the same column twice.
            schemaKeyIds.remove(config.partitionFieldId);
        }
        if (config.dataModel == DorisDataModelEnum.UNIQUE && schemaKeyIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "主键更新表至少需要一个非分区主键字段，分区字段由 Doris 自动加入 UNIQUE KEY");
        }
        List<String> keyNames = resolveFieldNames(schemaKeyIds, fieldsById, "主键");
        List<String> distributionNames =
                resolveFieldNames(config.distributionFieldIds, fieldsById, "HASH 分布");

        String partitionFieldName = null;
        if (config.partitionFieldId != null) {
            DimensionTableFieldPO partitionField = fieldsById.get(config.partitionFieldId);
            partitionFieldName = partitionField.getFieldName();
        }

        if (config.dataModel == DorisDataModelEnum.DUPLICATE
                && (partitionFieldName == null
                        || !partitionFieldName.equals(fields.get(0).getFieldName()))) {
            // Match Doris CDC's schema factory: DUPLICATE uses the first column as the implicit
            // schema key for column ordering and STRING-to-VARCHAR handling, without a UNIQUE clause.
            keyNames = Collections.singletonList(fields.get(0).getFieldName());
        }

        Map<String, FieldSchema> fieldSchemas = new LinkedHashMap<>();
        for (DimensionTableFieldPO field : fields) {
            String dorisType = DorisTypeMapper.toDorisType(toFlinkType(field));
            boolean keyField = keyNames.contains(field.getFieldName());
            boolean partitionField = field.getId().equals(config.partitionFieldId);
            if (Boolean.FALSE.equals(field.getCanNull()) && !keyField && !partitionField) {
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
        schema.setModel(DataModel.valueOf(config.dataModel.name()));
        schema.setKeys(keyNames);
        schema.setDistributeKeys(distributionNames);
        schema.setProperties(Collections.emptyMap());
        schema.setTableBuckets(config.bucketCount);
        if (partitionFieldName != null) {
            schema.setPartitionInfo(Tuple2.of(
                    partitionFieldName, config.partitionUnit.name().toLowerCase(Locale.ROOT)));
        }
        String generatedDdl = DorisSchemaFactory.generateCreateTableDDL(schema);
        String ddl = generatedDdl.replaceFirst(
                "(?i)^CREATE TABLE IF NOT EXISTS\\s+", "CREATE TABLE ");
        return new BuildResult(ddl, serializePhysicalConfig(config));
    }

    private ResolvedDorisConfig resolvePhysicalConfig(
            List<DimensionTableFieldPO> fields,
            Map<String, DimensionTableFieldPO> fieldsById,
            DimensionDorisPublishDTO.Advanced advanced) {
        ResolvedDorisConfig config = new ResolvedDorisConfig();
        config.keyFieldIds = fields.stream()
                .filter(field -> Boolean.TRUE.equals(field.getPrimaryFlag()))
                .map(DimensionTableFieldPO::getId)
                .collect(Collectors.toList());
        for (String keyFieldId : config.keyFieldIds) {
            DimensionTableFieldPO keyField = fieldsById.get(keyFieldId);
            if (!Boolean.FALSE.equals(keyField.getCanNull())) {
                throw new IllegalArgumentException(
                        "主键字段必须设置为非空，请检查字段: " + keyField.getFieldName());
            }
        }
        config.dataModel = config.keyFieldIds.isEmpty()
                ? DorisDataModelEnum.DUPLICATE : DorisDataModelEnum.UNIQUE;
        config.keyFieldNames = config.keyFieldIds.stream()
                .map(id -> fieldsById.get(id).getFieldName())
                .collect(Collectors.toList());
        config.distributionType = DorisDistributionTypeEnum.HASH;
        config.bucketMode = advanced != null && advanced.getBucketCount() != null
                ? DorisBucketModeEnum.FIXED : DorisBucketModeEnum.AUTO;
        config.bucketCount = advanced == null ? null : advanced.getBucketCount();
        if (config.bucketCount != null && config.bucketCount <= 0) {
            throw new IllegalArgumentException("固定分桶数量必须大于 0");
        }

        config.partitionStrategy = DorisPartitionStrategyEnum.NONE;
        if (advanced != null && advanced.getPartition() != null) {
            String partitionFieldId = StringUtils.trimToNull(advanced.getPartition().getFieldId());
            DorisPartitionUnitEnum partitionUnit = advanced.getPartition().getUnit();
            if (partitionFieldId == null || partitionUnit == null) {
                throw new IllegalArgumentException("启用时间分区后，必须选择分区字段和分区周期");
            }
            DimensionTableFieldPO partitionField = fieldsById.get(partitionFieldId);
            if (partitionField == null) {
                throw new IllegalArgumentException("分区字段不属于当前模型");
            }
            if (!isDateTimeType(partitionField.getDataType())) {
                throw new IllegalArgumentException("Doris 时间分区字段必须是 DATE 或时间类型");
            }
            if (!Boolean.FALSE.equals(partitionField.getCanNull())) {
                throw new IllegalArgumentException("Doris 时间分区字段必须设置为非空");
            }
            config.partitionStrategy = DorisPartitionStrategyEnum.AUTO_RANGE;
            config.partitionFieldId = partitionFieldId;
            config.partitionFieldName = partitionField.getFieldName();
            config.partitionUnit = partitionUnit;
        }

        boolean customDistribution = advanced != null
                && Boolean.TRUE.equals(advanced.getCustomDistribution());
        config.customDistribution = customDistribution;
        if (customDistribution) {
            config.distributionFieldIds = distinctIds(
                    advanced.getDistributionFieldIds(), "HASH 分布字段");
            if (config.distributionFieldIds.isEmpty()) {
                throw new IllegalArgumentException("自定义 HASH 分布至少选择一个字段");
            }
        } else {
            String preferredFieldId = config.keyFieldIds.stream()
                    .filter(id -> !id.equals(config.partitionFieldId))
                    .findFirst()
                    .orElse(fields.get(0).getId());
            config.distributionFieldIds = Collections.singletonList(preferredFieldId);
        }
        for (String distributionFieldId : config.distributionFieldIds) {
            if (!fieldsById.containsKey(distributionFieldId)) {
                throw new IllegalArgumentException("HASH 分布字段不属于当前模型: " + distributionFieldId);
            }
        }
        config.distributionFieldNames = config.distributionFieldIds.stream()
                .map(id -> fieldsById.get(id).getFieldName())
                .collect(Collectors.toList());
        return config;
    }

    private String serializePhysicalConfig(ResolvedDorisConfig config) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("dataModel", config.dataModel.name());
        snapshot.put("keyFieldIds", config.keyFieldIds);
        snapshot.put("keyFieldNames", config.keyFieldNames);
        snapshot.put("distributionType", config.distributionType.name());
        snapshot.put("distributionFieldIds", config.distributionFieldIds);
        snapshot.put("distributionFieldNames", config.distributionFieldNames);
        snapshot.put("distributionMode", config.customDistribution ? "CUSTOM" : "AUTO");
        snapshot.put("bucketMode", config.bucketMode.name());
        snapshot.put("bucketCount", config.bucketCount);
        snapshot.put("partitionStrategy", config.partitionStrategy.name());
        snapshot.put("partitionFieldId", config.partitionFieldId);
        snapshot.put("partitionFieldName", config.partitionFieldName);
        snapshot.put("partitionUnit", config.partitionUnit == null ? null : config.partitionUnit.name());
        snapshot.put("tableProperties", Collections.emptyMap());
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception ex) {
            throw new IllegalStateException("序列化 Doris 发布配置失败", ex);
        }
    }

    private void validatePublishRequest(DimensionDorisPublishDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Doris 发布参数不能为空");
        }
        if (dto.getDatasourceId() == null) {
            throw new IllegalArgumentException("请选择 Doris 数据源");
        }
        if (StringUtils.isBlank(dto.getTargetDatabase())) {
            throw new IllegalArgumentException("请输入目标数据库");
        }
    }

    private void requireSubmitted(DimensionTablePO table) {
        if (!Boolean.TRUE.equals(table.getSubmit())) {
            throw new IllegalArgumentException("请先提交维度模型，再预览或发布 Doris 建表");
        }
    }

    private static class ResolvedDorisConfig {
        private DorisDataModelEnum dataModel;
        private List<String> keyFieldIds = Collections.emptyList();
        private List<String> keyFieldNames = Collections.emptyList();
        private DorisDistributionTypeEnum distributionType;
        private List<String> distributionFieldIds = Collections.emptyList();
        private List<String> distributionFieldNames = Collections.emptyList();
        private boolean customDistribution;
        private DorisBucketModeEnum bucketMode;
        private Integer bucketCount;
        private DorisPartitionStrategyEnum partitionStrategy;
        private String partitionFieldId;
        private String partitionFieldName;
        private DorisPartitionUnitEnum partitionUnit;
    }

    private static class BuildResult {
        private final String ddl;
        private final String physicalConfig;

        private BuildResult(String ddl, String physicalConfig) {
            this.ddl = ddl;
            this.physicalConfig = physicalConfig;
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

    private List<String> distinctIds(List<String> ids, String label) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String id : ids) {
            String value = StringUtils.trimToNull(id);
            if (value == null || !seen.add(value)) {
                throw new IllegalArgumentException(label + "不能为空且不能重复");
            }
            result.add(value);
        }
        return result;
    }

    private List<String> resolveFieldNames(
            List<String> ids, Map<String, DimensionTableFieldPO> fieldsById, String label) {
        List<String> names = new ArrayList<>();
        for (String id : ids) {
            DimensionTableFieldPO field = fieldsById.get(id);
            if (field == null) {
                throw new IllegalArgumentException(label + "字段不属于当前模型: " + id);
            }
            names.add(field.getFieldName());
        }
        return names;
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

    private boolean tableMatchesDdl(
            Connection connection, String database, String table, String tableId) throws Exception {
        DatabaseMetaData metadata = connection.getMetaData();
        Map<String, String> expectedColumns = new HashMap<>();
        for (DimensionTableFieldPO field : listFields(tableId)) {
            expectedColumns.put(
                    field.getFieldName().toLowerCase(Locale.ROOT),
                    normalizeDorisTypeName(DorisTypeMapper.toDorisType(toFlinkType(field))));
        }

        Map<String, String> actualColumns = new HashMap<>();
        try (ResultSet columns = metadata.getColumns(
                database, null, metadataPattern(metadata, table), null)) {
            while (columns.next()) {
                String actualTable = columns.getString("TABLE_NAME");
                if (!table.equalsIgnoreCase(actualTable)) {
                    continue;
                }
                String columnName = columns.getString("COLUMN_NAME");
                String typeName = columns.getString("TYPE_NAME");
                if (columnName != null && typeName != null) {
                    actualColumns.put(
                            columnName.toLowerCase(Locale.ROOT),
                            normalizeDorisTypeName(typeName));
                }
            }
        }

        if (expectedColumns.isEmpty() || expectedColumns.size() != actualColumns.size()) {
            return false;
        }
        for (Map.Entry<String, String> expected : expectedColumns.entrySet()) {
            if (!expected.getValue().equals(actualColumns.get(expected.getKey()))) {
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

    private boolean isDateTimeType(DimensionDataTypeEnum type) {
        return type == DimensionDataTypeEnum.DATE
                || type == DimensionDataTypeEnum.DATETIME
                || type == DimensionDataTypeEnum.TIMESTAMP;
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
