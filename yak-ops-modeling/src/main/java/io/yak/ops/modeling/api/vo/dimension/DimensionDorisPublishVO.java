package io.yak.ops.modeling.api.vo.dimension;

import io.yak.ops.dao.entity.enums.DorisPublishStatusEnum;
import java.util.Date;
import lombok.Data;

/** DDL preview or persisted physical publish result. */
@Data
public class DimensionDorisPublishVO {
    private String id;
    private String tableId;
    private Integer versionNumber;
    private Integer datasourceId;
    private String datasourceName;
    private String targetDatabase;
    private String targetTable;
    private String ddl;
    private String ddlHash;
    /** Immutable resolved Doris physical configuration used to generate this DDL. */
    private String physicalConfig;
    private DorisPublishStatusEnum status;
    private String errorMessage;
    private Long executionDurationMs;
    private Date startedAt;
    private Date finishedAt;
    private Date createTime;
    private String createBy;
}
