package io.yak.ops.modeling.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.dao.entity.DataSource;
import io.yak.ops.dao.entity.DimensionDorisConfigPO;
import io.yak.ops.dao.entity.DimensionDorisDistributionFieldPO;
import io.yak.ops.dao.entity.DimensionDorisKeyFieldPO;
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
import io.yak.ops.dao.repository.modeling.DimensionDorisConfigDao;
import io.yak.ops.dao.repository.modeling.DimensionDorisDistributionFieldDao;
import io.yak.ops.dao.repository.modeling.DimensionDorisKeyFieldDao;
import io.yak.ops.dao.repository.modeling.DimensionDorisPublishDao;
import io.yak.ops.dao.repository.modeling.DimensionTableDao;
import io.yak.ops.dao.repository.modeling.DimensionTableFieldDao;
import io.yak.ops.dao.repository.modeling.VersionManagementDao;
import io.yak.ops.modeling.api.dto.dimension.DimensionDorisConfigDTO;
import io.yak.ops.modeling.api.dto.dimension.DimensionDorisPublishDTO;
import io.yak.ops.modeling.api.vo.dimension.DimensionDorisConfigVO;
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
import java.util.Arrays;
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
    private static final Set<String> ALLOWED_TABLE_PROPERTIES = new HashSet<>(Arrays.asList(
            "replication_num",
            "storage_medium",
            "storage_cooldown_time",
            "compression",
            "enable_unique_key_merge_on_write",
            "light_schema_change",
            "disable_auto_compaction",
            "write_quorum",
            "bloom_filter_columns",
            "bloom_filter_fpp",
            "inverted_index_storage_format",
            "storage_format"
    ));

    @Resource private ObjectMapper objectMapper;
    @Resource private DimensionTableDao dimensionTableDao;
    @Resource private DimensionTableFieldDao dimensionTableFieldDao;
    @Resource private DimensionDorisConfigDao dimensionDorisConfigDao;
    @Resource private DimensionDorisKeyFieldDao dimensionDorisKeyFieldDao;
    @Resource private DimensionDorisDistributionFieldDao dimensionDorisDistributionFieldDao;
    @Resource private DimensionDorisPublishDao dimensionDorisPublishDao;
    @Resource private VersionManagementDao versionManagementDao;

    @Override
    public DimensionDorisConfigVO getConfig(String tableId) {
        DimensionTablePO table = requirePhysicalTable(tableId);
        DimensionDorisConfigPO config = findConfig(tableId);
        List<DimensionDorisKeyFieldPO> keyRows = dimensionDorisKeyFieldDao.queryList(
                new QueryWrapper<DimensionDorisKeyFieldPO>()
                        .eq("table_id", tableId).orderByAsc("ordinal"));
        List<DimensionDorisDistributionFieldPO> distributionRows =
                dimensionDorisDistributionFieldDao.queryList(
                        new QueryWrapper<DimensionDorisDistributionFieldPO>()
                                .eq("table_id", tableId).orderByAsc("ordinal"));
        List<String> keyIds = safeList(keyRows).stream()
                .map(DimensionDorisKeyFieldPO::getFieldId).collect(Collectors.toList());
        List<String> distributionIds = safeList(distributionRows).stream()
                .map(DimensionDorisDistributionFieldPO::getFieldId).collect(Collectors.toList());
        return toConfigVO(table, config, keyIds, distributionIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DimensionDorisConfigVO saveConfig(
            String tableId, DimensionDorisConfigDTO dto, String operator) {
        DimensionTablePO table = requirePhysicalTable(tableId);
        if (dto == null) {
            throw new IllegalArgumentException("Doris 建表配置不能为空");
        }

        List<DimensionTableFieldPO> fields = listFields(tableId);
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("请先配置字段，再保存 Doris 建表配置");
        }
        Map<String, DimensionTableFieldPO> fieldsById = fields.stream()
                .collect(Collectors.toMap(DimensionTableFieldPO::getId, field -> field));
        DorisDataModelEnum dataModel = dto.getDataModel() == null
                ? DorisDataModelEnum.DUPLICATE : dto.getDataModel();
        DorisPartitionStrategyEnum partitionStrategy = dto.getPartitionStrategy() == null
                ? DorisPartitionStrategyEnum.NONE : dto.getPartitionStrategy();
        DorisDistributionTypeEnum distributionType = dto.getDistributionType() == null
                ? DorisDistributionTypeEnum.HASH : dto.getDistributionType();
        DorisBucketModeEnum bucketMode = dto.getBucketMode() == null
                ? DorisBucketModeEnum.AUTO : dto.getBucketMode();

        List<String> keyFieldIds = distinctIds(dto.getKeyFieldIds(), "Doris Key 字段");
        List<String> distributionFieldIds =
                distinctIds(dto.getDistributionFieldIds(), "Doris 分布字段");
        if (dataModel == DorisDataModelEnum.UNIQUE && keyFieldIds.isEmpty()) {
            throw new IllegalArgumentException("UNIQUE KEY 模型必须至少选择一个 Key 字段");
        }
        if (dataModel == DorisDataModelEnum.DUPLICATE && !keyFieldIds.isEmpty()) {
            throw new IllegalArgumentException("DUPLICATE KEY 模型不配置 UNIQUE KEY 字段");
        }
        if (distributionType != DorisDistributionTypeEnum.HASH) {
            throw new IllegalArgumentException("当前 Doris SDK 仅支持 HASH 分布");
        }
        if (distributionFieldIds.isEmpty()) {
            distributionFieldIds.add(fields.get(0).getId());
        }

        for (String fieldId : keyFieldIds) {
            DimensionTableFieldPO field = fieldsById.get(fieldId);
            if (field == null) {
                throw new IllegalArgumentException("Doris Key 字段不属于当前模型: " + fieldId);
            }
            if (!Boolean.FALSE.equals(field.getCanNull())) {
                throw new IllegalArgumentException("UNIQUE KEY 字段必须设置为非空: " + field.getFieldName());
            }
        }
        for (String fieldId : distributionFieldIds) {
            if (!fieldsById.containsKey(fieldId)) {
                throw new IllegalArgumentException("Doris 分布字段不属于当前模型: " + fieldId);
            }
        }

        String partitionFieldId = StringUtils.trimToNull(dto.getPartitionFieldId());
        DorisPartitionUnitEnum partitionUnit = dto.getPartitionUnit();
        if (partitionStrategy == DorisPartitionStrategyEnum.NONE) {
            partitionFieldId = null;
            partitionUnit = null;
        } else {
            if (partitionStrategy != DorisPartitionStrategyEnum.AUTO_RANGE) {
                throw new IllegalArgumentException("当前 Doris SDK 仅支持 AUTO_RANGE 分区");
            }
            if (partitionFieldId == null || partitionUnit == null) {
                throw new IllegalArgumentException("自动分区必须指定分区字段和分区周期");
            }
            DimensionTableFieldPO partitionField = fieldsById.get(partitionFieldId);
            if (partitionField == null) {
                throw new IllegalArgumentException("分区字段不属于当前模型");
            }
            if (!isDateTimeType(partitionField.getDataType())) {
                throw new IllegalArgumentException("Doris AUTO_RANGE 分区字段必须是 DATE 或时间类型");
            }
            if (!Boolean.FALSE.equals(partitionField.getCanNull())) {
                throw new IllegalArgumentException("Doris AUTO_RANGE 分区字段必须设置为非空");
            }
            if (keyFieldIds.contains(partitionFieldId)) {
                throw new IllegalArgumentException("AUTO_RANGE 分区字段会自动加入 UNIQUE KEY，请勿重复选择");
            }
        }
        if (bucketMode == DorisBucketModeEnum.FIXED
                && (dto.getBucketCount() == null || dto.getBucketCount() <= 0)) {
            throw new IllegalArgumentException("固定分桶数量必须大于 0");
        }
        if (bucketMode == DorisBucketModeEnum.AUTO && dto.getBucketCount() != null) {
            throw new IllegalArgumentException("AUTO 分桶不需要指定 bucketCount");
        }

        Map<String, String> properties = normalizeProperties(dto.getTableProperties());
        DimensionDorisConfigPO existing = findConfig(tableId);
        DimensionDorisConfigPO po = new DimensionDorisConfigPO();
        po.setTableId(tableId);
        po.setDataModel(dataModel);
        po.setPartitionStrategy(partitionStrategy);
        po.setPartitionFieldId(partitionFieldId);
        po.setPartitionUnit(partitionUnit);
        po.setDistributionType(distributionType);
        po.setBucketMode(bucketMode);
        po.setBucketCount(bucketMode == DorisBucketModeEnum.FIXED ? dto.getBucketCount() : null);
        try {
            po.setTableProperties(objectMapper.writeValueAsString(properties));
        } catch (Exception ex) {
            throw new IllegalStateException("序列化 Doris 表属性失败", ex);
        }
        po.setProjectCode(null);
        if (existing == null) {
            po.initInsert();
            po.setCreateBy(StringUtils.defaultIfBlank(operator, "sys"));
            po.setUpdateBy(StringUtils.defaultIfBlank(operator, "sys"));
            if (dimensionDorisConfigDao.insert(po) <= 0) {
                throw new IllegalArgumentException("保存 Doris 建表配置失败");
            }
        } else {
            po.setId(existing.getId());
            po.setCreateTime(existing.getCreateTime());
            po.setCreateBy(existing.getCreateBy());
            po.initUpdate();
            po.setUpdateBy(StringUtils.defaultIfBlank(operator, "sys"));
            if (!dimensionDorisConfigDao.updateById(po)) {
                throw new IllegalArgumentException("更新 Doris 建表配置失败");
            }
        }

        dimensionDorisKeyFieldDao.deleteByWrapper(
                new QueryWrapper<DimensionDorisKeyFieldPO>().eq("table_id", tableId));
        for (int index = 0; index < keyFieldIds.size(); index++) {
            DimensionDorisKeyFieldPO key = new DimensionDorisKeyFieldPO();
            key.setTableId(tableId);
            key.setFieldId(keyFieldIds.get(index));
            key.setOrdinal(index);
            key.setProjectCode(null);
            key.initInsert();
            key.setCreateBy(StringUtils.defaultIfBlank(operator, "sys"));
            key.setUpdateBy(StringUtils.defaultIfBlank(operator, "sys"));
            dimensionDorisKeyFieldDao.insert(key);
        }

        dimensionDorisDistributionFieldDao.deleteByWrapper(
                new QueryWrapper<DimensionDorisDistributionFieldPO>().eq("table_id", tableId));
        for (int index = 0; index < distributionFieldIds.size(); index++) {
            DimensionDorisDistributionFieldPO distribution =
                    new DimensionDorisDistributionFieldPO();
            distribution.setTableId(tableId);
            distribution.setFieldId(distributionFieldIds.get(index));
            distribution.setOrdinal(index);
            distribution.setProjectCode(null);
            distribution.initInsert();
            distribution.setCreateBy(StringUtils.defaultIfBlank(operator, "sys"));
            distribution.setUpdateBy(StringUtils.defaultIfBlank(operator, "sys"));
            dimensionDorisDistributionFieldDao.insert(distribution);
        }

        markDraft(table, operator);
        return getConfig(tableId);
    }

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
        if (dto == null) {
            throw new IllegalArgumentException("Doris 发布参数不能为空");
        }
        validateDatasource(datasource);
        String ddl = buildDdl(tableId, dto.getTargetDatabase());
        DimensionDorisPublishVO vo = new DimensionDorisPublishVO();
        vo.setTableId(tableId);
        vo.setDatasourceId(datasource.getId());
        vo.setDatasourceName(datasource.getName());
        vo.setTargetDatabase(dto.getTargetDatabase());
        vo.setTargetTable(requireTable(tableId).getTableName());
        vo.setDdl(ddl);
        vo.setDdlHash(sha256(ddl));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DimensionDorisPublishVO publish(
            String tableId, DimensionDorisPublishDTO dto, DataSource datasource, String operator) {
        if (dto == null) {
            throw new IllegalArgumentException("Doris 发布参数不能为空");
        }
        validateDatasource(datasource);

        // Serialize publishes for one model across application instances. The model-row lock is
        // held until the external DDL and the publish-history update have both completed.
        DimensionTablePO table = lockPhysicalTable(tableId);
        if (!Boolean.TRUE.equals(table.getSubmit())) {
            throw new IllegalArgumentException("请先提交模型版本，再执行物理发布");
        }

        String ddl = buildDdl(tableId, dto.getTargetDatabase());
        String ddlHash = sha256(ddl);
        VersionManagementPO version = latestVersion(tableId);
        List<DimensionDorisPublishPO> previousRecords = dimensionDorisPublishDao.queryList(
                new QueryWrapper<DimensionDorisPublishPO>()
                        .eq("table_id", tableId)
                        .eq("datasource_id", datasource.getId())
                        .eq("target_database", dto.getTargetDatabase())
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
        publish.setTargetDatabase(dto.getTargetDatabase());
        publish.setTargetTable(table.getTableName());
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
                    tableExists(connection, dto.getTargetDatabase(), table.getTableName());
            if (targetExists) {
                if (previous != null
                        && tableMatchesDdl(
                                connection, dto.getTargetDatabase(), table.getTableName(), tableId)) {
                    // The same DDL was successfully published before and the live columns still
                    // match. Treat this as an idempotent no-op, not as a new CREATE operation.
                    publish.setStatus(DorisPublishStatusEnum.SUCCESS);
                } else {
                    publish.setStatus(DorisPublishStatusEnum.CONFLICT);
                    publish.setErrorMessage(
                            previous == null
                                    ? "目标 Doris 表已存在。为避免覆盖现有数据，本次发布未执行 DDL。"
                                    : "目标 Doris 表结构与当前模型不一致。为避免覆盖现有数据，本次发布未执行 DDL。");
                }
            } else {
                // A previous SUCCESS record is not proof that the table still exists. If an
                // administrator dropped the table, recreate it from the current model snapshot.
                try (Statement statement = connection.createStatement()) {
                    statement.execute(ddl);
                } catch (SQLException ex) {
                    // Another model or an external actor may create the target after our
                    // existence check. The generated DDL is strict CREATE, so this is surfaced
                    // as a conflict instead of silently succeeding through IF NOT EXISTS.
                    if (tableExists(connection, dto.getTargetDatabase(), table.getTableName())) {
                        publish.setStatus(DorisPublishStatusEnum.CONFLICT);
                        publish.setErrorMessage(
                                "目标 Doris 表在发布过程中已被创建，本次发布未覆盖现有表。");
                    } else {
                        throw ex;
                    }
                }
                if (publish.getStatus() != DorisPublishStatusEnum.CONFLICT) {
                    if (tableMatchesDdl(
                            connection, dto.getTargetDatabase(), table.getTableName(), tableId)) {
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

    private String buildDdl(String tableId, String targetDatabase) {
        DimensionTablePO table = requirePhysicalTable(tableId);
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

        DimensionDorisConfigPO config = findConfig(tableId);
        if (config == null) {
            config = defaultConfig(tableId);
        }
        if (config.getDataModel() == null) {
            config.setDataModel(DorisDataModelEnum.DUPLICATE);
        }
        if (config.getPartitionStrategy() == null) {
            config.setPartitionStrategy(DorisPartitionStrategyEnum.NONE);
        }
        if (config.getDistributionType() == null) {
            config.setDistributionType(DorisDistributionTypeEnum.HASH);
        }
        if (config.getBucketMode() == null) {
            config.setBucketMode(DorisBucketModeEnum.AUTO);
        }
        List<DimensionDorisKeyFieldPO> keyRows = dimensionDorisKeyFieldDao.queryList(
                new QueryWrapper<DimensionDorisKeyFieldPO>()
                        .eq("table_id", tableId).orderByAsc("ordinal"));
        List<DimensionDorisDistributionFieldPO> distributionRows =
                dimensionDorisDistributionFieldDao.queryList(
                        new QueryWrapper<DimensionDorisDistributionFieldPO>()
                                .eq("table_id", tableId).orderByAsc("ordinal"));
        List<String> keyIds = safeList(keyRows).stream()
                .map(DimensionDorisKeyFieldPO::getFieldId).collect(Collectors.toList());
        List<String> distributionIds = safeList(distributionRows).stream()
                .map(DimensionDorisDistributionFieldPO::getFieldId).collect(Collectors.toList());

        if (config.getDataModel() == DorisDataModelEnum.UNIQUE && keyIds.isEmpty()) {
            throw new IllegalArgumentException("UNIQUE KEY 模型必须配置 Key 字段");
        }
        if (config.getDataModel() == DorisDataModelEnum.DUPLICATE && !keyIds.isEmpty()) {
            throw new IllegalArgumentException("DUPLICATE KEY 模型不能配置 UNIQUE KEY 字段");
        }
        if (new HashSet<>(keyIds).size() != keyIds.size()) {
            throw new IllegalArgumentException("Doris Key 字段不能重复");
        }
        if (new HashSet<>(distributionIds).size() != distributionIds.size()) {
            throw new IllegalArgumentException("Doris HASH 分布字段不能重复");
        }
        if (config.getDistributionType() != DorisDistributionTypeEnum.HASH) {
            throw new IllegalArgumentException("当前 Doris SDK 仅支持 HASH 分布");
        }
        if (config.getBucketMode() == DorisBucketModeEnum.FIXED
                && (config.getBucketCount() == null || config.getBucketCount() <= 0)) {
            throw new IllegalArgumentException("固定分桶数量必须大于 0");
        }
        if (config.getBucketMode() == DorisBucketModeEnum.AUTO && config.getBucketCount() != null) {
            throw new IllegalArgumentException("AUTO 分桶不应配置 bucketCount");
        }
        if (distributionIds.isEmpty()) {
            distributionIds.add(fields.get(0).getId());
        }

        for (String keyId : keyIds) {
            DimensionTableFieldPO keyField = fieldsById.get(keyId);
            if (keyField == null) {
                throw new IllegalArgumentException("Doris Key 字段不属于当前模型: " + keyId);
            }
            // Revalidate at DDL time because fields can be edited after the Doris config is saved.
            if (!Boolean.FALSE.equals(keyField.getCanNull())) {
                throw new IllegalArgumentException(
                        "UNIQUE KEY 字段必须设置为非空: " + keyField.getFieldName());
            }
        }

        List<String> keyNames = resolveFieldNames(keyIds, fieldsById, "Key");
        List<String> distributionNames =
                resolveFieldNames(distributionIds, fieldsById, "HASH 分布");
        Map<String, String> properties = parseProperties(config.getTableProperties());

        String partitionFieldName = null;
        if (config.getPartitionStrategy() == DorisPartitionStrategyEnum.AUTO_RANGE) {
            DimensionTableFieldPO partitionField = fieldsById.get(config.getPartitionFieldId());
            if (partitionField == null || !isDateTimeType(partitionField.getDataType())) {
                throw new IllegalArgumentException(
                        "Doris AUTO_RANGE 分区字段必须是当前模型中的 DATE 或时间字段");
            }
            if (config.getPartitionUnit() == null) {
                throw new IllegalArgumentException("Doris AUTO_RANGE 分区必须指定周期");
            }
            if (!Boolean.FALSE.equals(partitionField.getCanNull())) {
                throw new IllegalArgumentException("Doris AUTO_RANGE 分区字段必须设置为非空");
            }
            if (keyIds.contains(partitionField.getId())) {
                throw new IllegalArgumentException("AUTO_RANGE 分区字段会自动加入 UNIQUE KEY，请勿重复选择");
            }
            partitionFieldName = partitionField.getFieldName();
        }

        if (config.getDataModel() == DorisDataModelEnum.DUPLICATE
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
            boolean partitionField = field.getId().equals(config.getPartitionFieldId());
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
        schema.setModel(DataModel.valueOf(config.getDataModel().name()));
        schema.setKeys(keyNames);
        schema.setDistributeKeys(distributionNames);
        schema.setProperties(properties);
        schema.setTableBuckets(config.getBucketMode() == DorisBucketModeEnum.FIXED
                ? config.getBucketCount() : null);
        if (partitionFieldName != null) {
            schema.setPartitionInfo(Tuple2.of(
                    partitionFieldName, config.getPartitionUnit().name().toLowerCase(Locale.ROOT)));
        }
        String generatedDdl = DorisSchemaFactory.generateCreateTableDDL(schema);
        // The connector emits IF NOT EXISTS by default. Physical publishing must use strict
        // CREATE so a table created concurrently by another model cannot be reported as success.
        return generatedDdl.replaceFirst(
                "(?i)^CREATE TABLE IF NOT EXISTS\\s+", "CREATE TABLE ");
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

    private Map<String, String> normalizeProperties(Map<String, String> source) {
        Map<String, String> result = new LinkedHashMap<>();
        if (source == null) {
            return result;
        }
        for (Map.Entry<String, String> entry : source.entrySet()) {
            String key = StringUtils.trimToNull(entry.getKey());
            String value = StringUtils.trimToNull(entry.getValue());
            if (key == null || value == null || !ALLOWED_TABLE_PROPERTIES.contains(key)) {
                throw new IllegalArgumentException("不支持的 Doris 表属性: " + entry.getKey());
            }
            if (value.length() > 255 || value.indexOf('\'') >= 0 || value.indexOf('\\') >= 0
                    || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
                throw new IllegalArgumentException("Doris 表属性值包含不支持的字符: " + key);
            }
            result.put(key, value);
        }
        return result;
    }

    private Map<String, String> parseProperties(String json) {
        if (StringUtils.isBlank(json) || "{}".equals(json.trim())) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, String> parsed = objectMapper.readValue(
                    json, new TypeReference<Map<String, String>>() {});
            return normalizeProperties(parsed);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Doris 表属性配置格式错误", ex);
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

    private DimensionDorisConfigPO findConfig(String tableId) {
        return dimensionDorisConfigDao.queryOne(
                new QueryWrapper<DimensionDorisConfigPO>().eq("table_id", tableId));
    }

    private DimensionDorisConfigPO defaultConfig(String tableId) {
        DimensionDorisConfigPO config = new DimensionDorisConfigPO();
        config.setTableId(tableId);
        config.setDataModel(DorisDataModelEnum.DUPLICATE);
        config.setPartitionStrategy(DorisPartitionStrategyEnum.NONE);
        config.setDistributionType(DorisDistributionTypeEnum.HASH);
        config.setBucketMode(DorisBucketModeEnum.AUTO);
        config.setTableProperties("{}");
        return config;
    }

    private DimensionDorisConfigVO toConfigVO(
            DimensionTablePO table,
            DimensionDorisConfigPO config,
            List<String> keyIds,
            List<String> distributionIds) {
        if (config == null) {
            config = defaultConfig(table.getId());
        }
        DimensionDorisConfigVO vo = new DimensionDorisConfigVO();
        BeanUtils.copyProperties(config, vo);
        vo.setTableId(table.getId());
        vo.setKeyFieldIds(keyIds == null ? Collections.emptyList() : keyIds);
        vo.setDistributionFieldIds(
                distributionIds == null ? Collections.emptyList() : distributionIds);
        vo.setTableProperties(parseProperties(config.getTableProperties()));
        return vo;
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

    private void markDraft(DimensionTablePO table, String operator) {
        UpdateWrapper<DimensionTablePO> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", table.getId())
                .set("submit", Boolean.FALSE)
                .set("update_time", new Date())
                .set("update_by", StringUtils.defaultIfBlank(operator, "sys"));
        dimensionTableDao.update(null, wrapper);
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
