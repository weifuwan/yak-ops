package io.yak.ops.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.dao.entity.enums.DorisDataModelEnum;
import io.yak.ops.dao.entity.enums.DorisPublishModeEnum;
import io.yak.ops.dao.entity.enums.DorisPublishStatusEnum;
import java.util.Date;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Immutable DDL and execution result for one physical-table publish attempt. */
@Data
@TableName("t_yak_ops_dimension_table_publish")
@EqualsAndHashCode(callSuper = true)
public class DimensionDorisPublishPO extends ModelingBasePO {
    private String tableId;
    private String versionId;
    private Integer versionNumber;
    private Integer datasourceId;
    private DorisDataModelEnum tableModel;
    private DorisPublishModeEnum publishMode;
    private String publishRemark;
    /** Logical Key columns captured for safe incremental-publish comparison. */
    private String keyFields;
    private String datasourceName;
    private String targetDatabase;
    private String targetTable;
    @TableField("ddl_content")
    private String ddl;
    private String ddlHash;
    /** Physical SHOW CREATE TABLE snapshot captured after a successful publish. */
    private String physicalSchema;
    /** Legacy snapshot retained for reading historical rows; V1 no longer writes it. */
    @Deprecated
    private String physicalConfig;
    private DorisPublishStatusEnum status;
    private String errorMessage;
    private Long executionDurationMs;
    private Date startedAt;
    private Date finishedAt;
}
