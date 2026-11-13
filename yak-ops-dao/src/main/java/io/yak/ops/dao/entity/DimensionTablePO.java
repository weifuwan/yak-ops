package io.yak.ops.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.dao.entity.enums.DimensionStoragePolicyEnum;
import io.yak.ops.dao.entity.enums.DimensionTableTypeEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Dimension/physical model table. Every row is isolated by the inherited project_code. */
@Data
@TableName("t_yak_ops_dimension_table")
@EqualsAndHashCode(callSuper = true)
public class DimensionTablePO extends ModelingBasePO {
    private String dwhId;
    private String busTypeId;
    private String busProcessId;
    private String domainId;
    private String martId;
    private String subjectId;
    private String timePeriodId;
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
}