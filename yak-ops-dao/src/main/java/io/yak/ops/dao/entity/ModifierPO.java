package io.yak.ops.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import io.yak.ops.dao.entity.enums.DwTypeEnum;

/** Modeling asset persistence entity. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_yak_ops_indicator_modifier")
public class ModifierPO extends ModelingBasePO {
    private String modifierType;
            private String enShortName;
            private String enName;
            private String cnName;
            private String director;
            private String businessCaliber;
            private String remark;
            private Boolean submit;
            private DwTypeEnum dwType;
            private String martId;
            private String subjectId;
            private String busTypeId;
            private String domainId;
            private String dimensionTbId;
            private String dimensionFdId;
            private String codeId;
            private String codeDetailId;
}
