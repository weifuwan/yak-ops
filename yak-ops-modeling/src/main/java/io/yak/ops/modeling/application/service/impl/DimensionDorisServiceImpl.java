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
import io.yak.ops.modeling.ddl.doris.DorisDataSourceAdapter;
import io.yak.ops.modeling.ddl.doris.DorisDataSourceContext;
import io.yak.ops.modeling.ddl.doris.DorisDdlEngine;
import io.yak.ops.modeling.ddl.doris.DorisSchemaVerificationException;
import io.yak.ops.modeling.ddl.model.DdlColumnDefinition;
import io.yak.ops.modeling.ddl.model.DdlPlan;
import io.yak.ops.modeling.ddl.model.DdlTableDefinition;
import io.yak.ops.modeling.ddl.model.DorisDdlRequest;
import io.yak.ops.modeling.ddl.model.DorisFieldTypeOption;
import io.yak.ops.spi.enums.DbType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

/** Coordinates dimension-model business state with the isolated Doris DDL capability. */
@Service
public class DimensionDorisServiceImpl implements DimensionDorisService {

    private static final Logger log = LoggerFactory.getLogger(DimensionDorisServiceImpl.class);
    private static final String VERSION_TYPE = "DIMENSION_TABLE";

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private DimensionTableDao dimensionTableDao;

    @Resource
    private DimensionTableFieldDao dimensionTableFieldDao;

    @Resource
    private DimensionDorisPublishDao dimensionDorisPublishDao;

    @Resource
    private VersionManagementDao versionManagementDao;

    @Resource
    private DorisDdlEngine ddlEngine;

    @Resource
    private DorisDataSourceAdapter dataSourceAdapter;

    @Resource
    private DimensionDorisPublishRecordService publishRecordService;

    @Override
    public List<DimensionDorisFieldTypeOptionVO> getFieldTypeOptions() {
        return ddlEngine.getFieldTypeOptions().stream()
                .map(this::toFieldTypeOptionVO)
                .collect(Collectors.toList());
    }

    @Override
    public DimensionDorisPublishVO preview(
            String tableId, DimensionDorisPublishDTO dto, DataSource datasource) {
        validatePublishRequest(dto, false);
        validateDatasource(datasource);

        DimensionTablePO table = requirePhysicalTable(tableId);
        requireSubmitted(table);
        List<DimensionTableFieldPO> fields = listFields(tableId);
        try (DorisDataSourceContext context = dataSourceAdapter.open(datasource)) {
            String database = context.getDatabase();
            DorisDdlRequest request = buildDdlRequest(table, fields, dto, database);
            DdlPlan plan = ddlEngine.plan(context.getConnection(), context.getCatalog(), request);
            DimensionDorisPublishVO vo = new DimensionDorisPublishVO();
            vo.setTableId(tableId);
            vo.setDatasourceId(datasource.getId());
            vo.setDatasourceName(datasource.getName());
            vo.setTargetDatabase(database);
            vo.setTargetTable(table.getTableName());
            vo.setTableModel(dto.getTableModel());
            vo.setPublishMode(dto.getPublishMode());
            vo.setPublishRemark(StringUtils.trimToNull(dto.getPublishRemark()));
            vo.setDdl(plan.getDdl());
            vo.setDdlHash(sha256(plan.getDdl()));
            vo.setNoChanges(plan.isNoChanges());
            vo.setReplicationNum(plan.getReplicationNum());
            vo.setAvailableBackendCount(plan.getAvailableBackendCount());
            vo.setReplicationAuto(plan.isReplicationAuto());
            vo.setReplicationWarning(plan.getReplicationWarning());
            vo.setTargetTableExists(plan.isTargetTableExists());
            vo.setAddedColumns(plan.getAddedColumns());
            vo.setDroppedColumns(plan.getDroppedColumns());
            vo.setRequiresConfirmation(plan.isRequiresConfirmation());
            vo.setSchemaDrift(plan.isSchemaDrift());
            return vo;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "连接 Doris 生成 DDL 预览失败: " + errorMessage(ex), ex);
        }
    }

    @Override
    public DimensionDorisPublishVO publish(
            String tableId, DimensionDorisPublishDTO dto, DataSource datasource, String operator) {
        validatePublishRequest(dto, true);
        validateDatasource(datasource);
        if (dto.getPublishMode() == DorisPublishModeEnum.DROP_RECREATE
                && !Boolean.TRUE.equals(dto.getConfirmDrop())) {
            throw new IllegalArgumentException("删除重建需要二次确认");
        }

        DimensionTablePO table = requirePhysicalTable(tableId);
        requireSubmitted(table);
        VersionManagementPO version = latestVersion(tableId);
        List<DimensionTableFieldPO> fields = listFields(tableId);
        String database = null;
        String currentOperator = StringUtils.defaultIfBlank(operator, "sys");
        long startedMillis = System.currentTimeMillis();
        Date startedAt = null;
        DimensionDorisPublishPO publish = null;

        try (DorisDataSourceContext context = dataSourceAdapter.open(datasource)) {
            database = context.getDatabase();
            DorisDdlRequest request = buildDdlRequest(table, fields, dto, database);
            DdlPlan plan = ddlEngine.plan(context.getConnection(), context.getCatalog(), request);
            if (plan.isRequiresConfirmation() && !Boolean.TRUE.equals(dto.getConfirmDropColumns())) {
                throw new IllegalArgumentException("增量发布包含删除字段操作，需要二次确认");
            }
            if ((dto.getPublishMode() == DorisPublishModeEnum.DROP_RECREATE || plan.isRequiresConfirmation())
                    && !StringUtils.equalsIgnoreCase(dto.getConfirmedDdlHash(), sha256(plan.getDdl()))) {
                throw new IllegalArgumentException("目标物理表结构已变化，请刷新 DDL 预览并重新确认后再发布");
            }
            DimensionDorisPublishPO pending = createPublishRecord(
                    table, version, datasource, dto, plan, database, currentOperator);
            startedAt = new Date();
            publish = publishRecordService.createRunning(tableId, pending, startedAt);

            try {
                log.info(
                        "Doris publish started: publishId={}, tableId={}, datasourceId={}, database={}, table={}, mode={}, model={}, ddlStatements={}",
                        publish.getId(), tableId, datasource.getId(), database, table.getTableName(),
                        dto.getPublishMode(), dto.getTableModel(), plan.getStatements().size());
                String physicalSchema = ddlEngine.execute(
                        context.getConnection(), context.getCatalog(), request, plan, publish.getId());
                publishRecordService.updatePhysicalSchema(
                        publish.getId(), physicalSchema, currentOperator);
                publish.setPhysicalSchema(physicalSchema);
                Date finishedAt = new Date();
                long duration = System.currentTimeMillis() - startedMillis;
                publishRecordService.updateStatus(
                        publish.getId(), DorisPublishStatusEnum.SUCCESS, null,
                        startedAt, finishedAt, duration, currentOperator);
                publish.setStatus(DorisPublishStatusEnum.SUCCESS);
                publish.setFinishedAt(finishedAt);
                publish.setExecutionDurationMs(duration);
                log.info(
                        "Doris publish completed: publishId={}, tableId={}, datasourceId={}, database={}, table={}, mode={}, model={}, durationMs={}, noChanges={}",
                        publish.getId(), tableId, datasource.getId(), database, table.getTableName(),
                        dto.getPublishMode(), dto.getTableModel(), duration, plan.isNoChanges());
            } catch (Exception ex) {
                Date finishedAt = new Date();
                long duration = System.currentTimeMillis() - startedMillis;
                boolean verificationFailed = ex instanceof DorisSchemaVerificationException;
                DorisPublishStatusEnum failureStatus = verificationFailed
                        ? DorisPublishStatusEnum.VERIFY_FAILED
                        : DorisPublishStatusEnum.FAILED;
                if (verificationFailed) {
                    String physicalSchema =
                            ((DorisSchemaVerificationException) ex).getPhysicalSchema();
                    if (StringUtils.isNotBlank(physicalSchema)) {
                        try {
                            publishRecordService.updatePhysicalSchema(
                                    publish.getId(), physicalSchema, currentOperator);
                            publish.setPhysicalSchema(physicalSchema);
                        } catch (Exception persistenceException) {
                            ex.addSuppressed(persistenceException);
                            log.error(
                                    "Failed to persist observed Doris schema after verification failure: publishId={}",
                                    publish.getId(), persistenceException);
                        }
                    }
                }
                String message = errorMessage(ex);
                publishRecordService.updateStatus(
                        publish.getId(), failureStatus, message,
                        startedAt, finishedAt, duration, currentOperator);
                publish.setStatus(failureStatus);
                publish.setErrorMessage(StringUtils.left(message, 4000));
                publish.setFinishedAt(finishedAt);
                publish.setExecutionDurationMs(duration);
                log.error(
                        "Doris publish {}: publishId={}, tableId={}, datasourceId={}, database={}, table={}, mode={}, model={}, durationMs={}",
                        verificationFailed ? "verification failed after DDL execution" : "DDL execution failed",
                        publish.getId(), tableId, datasource.getId(), database, table.getTableName(),
                        dto.getPublishMode(), dto.getTableModel(), duration, ex);
            }
        } catch (Exception ex) {
            if (publish == null) {
                if (!(ex instanceof IllegalArgumentException)) {
                    log.error(
                            "Doris publish planning failed: tableId={}, datasourceId={}, database={}, table={}, mode={}, model={}",
                            tableId, datasource.getId(), database, table.getTableName(),
                            dto.getPublishMode(), dto.getTableModel(), ex);
                }
                if (ex instanceof IllegalArgumentException) {
                    throw (IllegalArgumentException) ex;
                }
                throw new IllegalArgumentException("Doris 发布失败: " + errorMessage(ex), ex);
            }

            if (publish.getStatus() == DorisPublishStatusEnum.PENDING
                    || publish.getStatus() == DorisPublishStatusEnum.RUNNING) {
                markPublishFailed(
                        publish, startedAt, startedMillis, currentOperator, ex);
            } else {
                log.warn(
                        "Doris connection cleanup failed after publish reached terminal state: publishId={}, status={}",
                        publish.getId(), publish.getStatus(), ex);
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
                        .eq("table_id", tableId)
                        .orderByDesc("create_time"));
        return safeList(records).stream().map(this::toPublishVO).collect(Collectors.toList());
    }

    private DorisDdlRequest buildDdlRequest(
            DimensionTablePO table,
            List<DimensionTableFieldPO> fields,
            DimensionDorisPublishDTO dto,
            String database) {
        DorisDataModelEnum previousModel = null;
        List<String> previousKeys = Collections.emptyList();
        String previousPhysicalSchema = null;
        if (dto.getPublishMode() == DorisPublishModeEnum.INCREMENTAL) {
            DimensionDorisPublishPO previous = latestSuccessfulPublish(
                    table.getId(), dto.getDatasourceId(), database, table.getTableName());
            if (previous != null) {
                previousModel = inferPreviousTableModel(previous);
                previousKeys = readPreviousKeyFields(previous, previousModel);
                previousPhysicalSchema = previous.getPhysicalSchema();
            }
        }
        return new DorisDdlRequest(
                toTableDefinition(table, fields, database),
                dto.getTableModel(),
                dto.getPublishMode(),
                previousModel,
                previousKeys,
                dto.getReplicationNum(),
                previousPhysicalSchema);
    }

    private DdlTableDefinition toTableDefinition(
            DimensionTablePO table, List<DimensionTableFieldPO> fields, String database) {
        List<DdlColumnDefinition> columns = new ArrayList<>();
        for (DimensionTableFieldPO field : fields) {
            columns.add(new DdlColumnDefinition(
                    field.getFieldName(),
                    field.getDataType(),
                    field.getLength(),
                    field.getFieldPrecision(),
                    field.getFieldScale(),
                    field.getDatetimePrecision(),
                    field.getCanNull(),
                    Boolean.TRUE.equals(field.getPrimaryFlag()),
                    field.getDefaultValue(),
                    StringUtils.defaultIfBlank(field.getRemark(), field.getCnName())));
        }
        return new DdlTableDefinition(
                database,
                table.getTableName(),
                StringUtils.defaultIfBlank(table.getCnName(), table.getRemark()),
                columns);
    }

    private DimensionDorisPublishPO createPublishRecord(
            DimensionTablePO table,
            VersionManagementPO version,
            DataSource datasource,
            DimensionDorisPublishDTO dto,
            DdlPlan plan,
            String database,
            String operator) {
        DimensionDorisPublishPO publish = new DimensionDorisPublishPO();
        publish.setTableId(table.getId());
        publish.setVersionId(version == null ? null : version.getId());
        publish.setVersionNumber(version == null ? null : version.getVersionNumber());
        publish.setDatasourceId(datasource.getId());
        publish.setDatasourceName(datasource.getName());
        publish.setTargetDatabase(database);
        publish.setTargetTable(table.getTableName());
        publish.setTableModel(dto.getTableModel());
        publish.setPublishMode(dto.getPublishMode());
        publish.setPublishRemark(dto.getPublishRemark().trim());
        publish.setKeyFields(serializeKeyFields(plan.getKeyFields()));
        publish.setDdl(plan.getDdl());
        publish.setDdlHash(sha256(plan.getDdl()));
        publish.setStatus(DorisPublishStatusEnum.PENDING);
        publish.setProjectCode(null);
        publish.initInsert();
        publish.setCreateBy(operator);
        publish.setUpdateBy(operator);
        return publish;
    }

    private void markPublishFailed(
            DimensionDorisPublishPO publish,
            Date startedAt,
            long startedMillis,
            String operator,
            Exception exception) {
        Date finishedAt = new Date();
        long duration = System.currentTimeMillis() - startedMillis;
        String message = errorMessage(exception);
        try {
            publishRecordService.updateStatus(
                    publish.getId(), DorisPublishStatusEnum.FAILED, message,
                    startedAt, finishedAt, duration, operator);
        } catch (Exception updateException) {
            log.error("Failed to persist Doris publish failure state: publishId={}",
                    publish.getId(), updateException);
        }
        publish.setStatus(DorisPublishStatusEnum.FAILED);
        publish.setErrorMessage(StringUtils.left(message, 4000));
        publish.setFinishedAt(finishedAt);
        publish.setExecutionDurationMs(duration);
        log.error("Doris publish interrupted: publishId={}, tableId={}",
                publish.getId(), publish.getTableId(), exception);
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

    private DorisDataModelEnum inferPreviousTableModel(DimensionDorisPublishPO previous) {
        if (previous.getTableModel() != null) {
            return previous.getTableModel();
        }
        return StringUtils.containsIgnoreCase(previous.getDdl(), "UNIQUE KEY")
                ? DorisDataModelEnum.UNIQUE
                : DorisDataModelEnum.DUPLICATE;
    }

    private List<String> readPreviousKeyFields(
            DimensionDorisPublishPO previous, DorisDataModelEnum previousModel) {
        if (StringUtils.isNotBlank(previous.getKeyFields())) {
            try {
                JsonNode fields = objectMapper.readTree(previous.getKeyFields());
                if (fields != null && fields.isArray()) {
                    List<String> names = new ArrayList<>();
                    fields.forEach(field -> {
                        if (field.isTextual()) {
                            names.add(field.asText());
                        }
                    });
                    return names;
                }
            } catch (JsonProcessingException ex) {
                throw new IllegalArgumentException("无法解析历史发布记录中的 Key 字段快照", ex);
            }
        }

        String ddl = StringUtils.defaultString(previous.getDdl());
        if (previousModel == DorisDataModelEnum.UNIQUE) {
            Matcher matcher = Pattern.compile("(?is)\\bUNIQUE\\s+KEY\\s*\\(([^)]*)\\)")
                    .matcher(ddl);
            if (!matcher.find()) {
                throw new IllegalArgumentException("无法识别历史 UNIQUE KEY 定义，请使用“删除重建”。");
            }
            List<String> names = new ArrayList<>();
            for (String part : matcher.group(1).split(",")) {
                String name = part.replace("`", "").trim();
                if (!name.isEmpty()) {
                    names.add(name);
                }
            }
            return names;
        }

        Matcher firstColumn = Pattern.compile("(?is)CREATE TABLE.*?\\(\\s*`([^`]+)`\\s+")
                .matcher(ddl);
        if (!firstColumn.find()) {
            throw new IllegalArgumentException("无法识别历史 DUPLICATE 表的首列定义，请使用“删除重建”。");
        }
        return Collections.singletonList(firstColumn.group(1));
    }

    private String serializeKeyFields(List<String> keyFields) {
        try {
            return objectMapper.writeValueAsString(keyFields);
        } catch (Exception ex) {
            throw new IllegalStateException("序列化 Doris Key 字段快照失败", ex);
        }
    }

    private VersionManagementPO latestVersion(String tableId) {
        List<VersionManagementPO> versions = versionManagementDao.queryList(
                new QueryWrapper<VersionManagementPO>()
                        .eq("source_table_id", tableId)
                        .eq("version_type", VERSION_TYPE)
                        .orderByDesc("version_number"));
        return safeList(versions).isEmpty() ? null : versions.get(0);
    }

    private DimensionTablePO requirePhysicalTable(String tableId) {
        if (StringUtils.isBlank(tableId)) {
            throw new IllegalArgumentException("维度表ID不能为空");
        }
        DimensionTablePO table = dimensionTableDao.queryById(tableId);
        if (table == null) {
            throw new IllegalArgumentException("维度建模记录不存在或不属于当前项目");
        }
        if (table.getTableType() == DimensionTableTypeEnum.DIM) {
            throw new IllegalArgumentException("DIM 是逻辑维度定义，不生成 Doris 物理表");
        }
        if (table.getTableType() == null) {
            throw new IllegalArgumentException("模型类型不能为空");
        }
        return table;
    }

    private void requireSubmitted(DimensionTablePO table) {
        if (!Boolean.TRUE.equals(table.getSubmit())) {
            throw new IllegalArgumentException("请先提交维度模型，再预览或发布 Doris 建表");
        }
    }

    private List<DimensionTableFieldPO> listFields(String tableId) {
        List<DimensionTableFieldPO> fields = dimensionTableFieldDao.queryList(
                new QueryWrapper<DimensionTableFieldPO>()
                        .eq("table_id", tableId)
                        .orderByAsc("ordinal")
                        .orderByAsc("create_time"));
        return safeList(fields);
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
        if (dto.getReplicationNum() != null
                && (dto.getReplicationNum() < 1 || dto.getReplicationNum() > 3)) {
            throw new IllegalArgumentException("Doris 副本数只能设置为 1、2 或 3");
        }
    }

    private void validateDatasource(DataSource datasource) {
        if (datasource == null || datasource.getType() != DbType.DORIS) {
            throw new IllegalArgumentException("物理建表发布目前仅支持 Doris 数据源");
        }
    }

    private DimensionDorisFieldTypeOptionVO toFieldTypeOptionVO(DorisFieldTypeOption option) {
        return new DimensionDorisFieldTypeOptionVO(
                option.getCode(),
                option.getLabel(),
                option.getParameterType(),
                option.getMaxLength(),
                option.getMaxPrecision(),
                option.getMaxScale());
    }

    private DimensionDorisPublishVO toPublishVO(DimensionDorisPublishPO publish) {
        if (publish == null) {
            return null;
        }
        DimensionDorisPublishVO vo = new DimensionDorisPublishVO();
        BeanUtils.copyProperties(publish, vo);
        if (publish.getStatus() != DorisPublishStatusEnum.VERIFY_FAILED) {
            vo.setPhysicalSchema(null);
        }
        vo.setNoChanges(
                StringUtils.startsWith(publish.getDdl(), "-- 当前 Doris 物理表结构与模型一致")
                        || StringUtils.startsWith(publish.getDdl(), "-- 当前目标表结构与模型一致"));
        return vo;
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

    private String errorMessage(Throwable exception) {
        String fallback = exception == null
                ? "Doris 发布失败"
                : exception.getClass().getSimpleName();
        Pattern replicationPattern = Pattern.compile(
                "replication\\s+num\\s+is\\s+(\\d+).*?available\\s+backend\\s+num\\s+is\\s+(\\d+)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        for (Throwable current = exception; current != null; current = current.getCause()) {
            String message = StringUtils.trimToNull(current.getMessage());
            if (message == null) {
                continue;
            }
            Matcher replicationError = replicationPattern.matcher(message);
            if (replicationError.find()) {
                return "Doris 集群副本配置不匹配：本次请求 "
                        + replicationError.group(1) + " 个副本，但当前可用 BE 只有 "
                        + replicationError.group(2)
                        + " 个。请调整副本策略或检查 Doris BE 状态。";
            }
            String normalized = message.toLowerCase(java.util.Locale.ROOT);
            if (normalized.contains("failed to find enough host with tag")
                    || (normalized.contains("replication") && normalized.contains("available backend"))) {
                return "Doris 集群指定 Tag 下的可用 BE 数量不足。请检查 BE 节点的 Alive、"
                        + "SystemDecommissioned 和 Tag 状态，并调整副本策略。";
            }
            if (fallback.equals(exception == null ? "" : exception.getClass().getSimpleName())) {
                fallback = message;
            }
        }
        return fallback;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? Collections.<T>emptyList() : values;
    }
}
