package io.yak.ops.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Modeling asset persistence entity. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_yak_ops_indicator_derivative")
public class DerivativeIndicatorPO extends ModelingBasePO {
    private String timePeriodId;
            private String modifierId;
            private String atomicId;
            private String dwhId;
            private String martId;
            private String subjectId;
            private String busTypeId;
            private String domainId;
            private String busProcessId;
            private String enShortName;
            private String enName;
            private String cnName;
            private String director;
            private String remark;
            private Boolean submit;
}
