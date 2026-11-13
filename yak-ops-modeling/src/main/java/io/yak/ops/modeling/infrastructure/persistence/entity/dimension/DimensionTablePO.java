package io.yak.ops.modeling.infrastructure.persistence.entity.dimension;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.modeling.domain.enums.DimensionStoragePolicyEnum;
import io.yak.ops.modeling.domain.enums.DimensionTableTypeEnum;
import io.yak.ops.modeling.infrastructure.persistence.entity.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Dimension/physical model table. Every row is isolated by the inherited project_code. */
@Data
@TableName("t_cockpit_dimension_table")
@EqualsAndHashCode(callSuper = true)
public class DimensionTablePO extends BasePO {
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