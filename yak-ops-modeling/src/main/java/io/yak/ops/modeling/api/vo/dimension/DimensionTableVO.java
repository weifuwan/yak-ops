package io.yak.ops.modeling.api.vo.dimension;

import io.yak.ops.dao.entity.enums.DimensionStoragePolicyEnum;
import io.yak.ops.dao.entity.enums.DimensionTableTypeEnum;
import lombok.Data;

import java.util.Date;
import java.util.List;

/** Response model for dimension table modeling. */
@Data
public class DimensionTableVO {
    private Integer currentVersion;
    private List<String> busProcessIds;
    private String id;
    private String dwhId;
    private String dwhName;
    private String martOrSubject;
    private String busTypeId;
    private String busProcessId;
    private String domainId;
    private String martId;
    private String subjectId;
    private String dimId;
    private String modifier;
    private String nameRuleId;
    private String tableName;
    private String cnName;
    private DimensionTableTypeEnum tableType;
    private DimensionStoragePolicyEnum storagePolicy;
    private Integer lifeCycle;
    private String director;
    private String remark;
    private Boolean submit;
    private String timePeriodId;
    private Date createTime;
    private String createBy;
    private Date updateTime;
    private String updateBy;
}