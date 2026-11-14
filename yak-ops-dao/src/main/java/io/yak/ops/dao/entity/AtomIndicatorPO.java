package io.yak.ops.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.dao.entity.enums.DwTypeEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Modeling asset persistence entity. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_yak_ops_indicator_atom")
public class AtomIndicatorPO extends ModelingBasePO {
    private String enShortName;
            private String enName;
            private String cnName;
            private String director;
            private String remark;
            private String businessCaliber;
            private String busTypeId;
            private String domainId;
            private String busProcessId;
            private DwTypeEnum dwType = DwTypeEnum.PUBLIC_LAYER;
            private String calculateFunction;
            private Integer decimalNumber;
            private String unitId;
            private Boolean removeDuplicates;
            private Boolean submit;
}
