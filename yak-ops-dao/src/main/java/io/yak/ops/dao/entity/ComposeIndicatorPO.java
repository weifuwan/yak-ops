package io.yak.ops.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Modeling asset persistence entity. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_yak_ops_indicator_compose")
public class ComposeIndicatorPO extends ModelingBasePO {
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
            private String computeMode;
            private String computeExpr;
            private String deriveId;
            private String compareType;
            private Boolean submit;
}
