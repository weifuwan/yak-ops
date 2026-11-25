package io.yak.ops.modeling.api.vo.dimension;

import io.yak.ops.dao.entity.enums.DorisDataModelEnum;
import io.yak.ops.dao.entity.enums.DorisPublishModeEnum;
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
    private DorisDataModelEnum tableModel;
    private DorisPublishModeEnum publishMode;
    private String publishRemark;
    private String datasourceName;
    private String targetDatabase;
    private String targetTable;
    private String ddl;
    private String ddlHash;
    private Boolean noChanges;
    private Integer replicationNum;
    private Integer availableBackendCount;
    private Boolean replicationAuto;
    private String replicationWarning;
    private DorisPublishStatusEnum status;
    private String errorMessage;
    private Long executionDurationMs;
    private Date startedAt;
    private Date finishedAt;
    private Date createTime;
    private String createBy;
}
