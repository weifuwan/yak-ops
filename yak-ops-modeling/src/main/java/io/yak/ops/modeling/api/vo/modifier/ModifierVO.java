package io.yak.ops.modeling.api.vo.modifier;

import io.yak.ops.modeling.api.vo.ModelingBaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import io.yak.ops.dao.entity.enums.DwTypeEnum;

/** Typed response model for Modifier. */
@Data
@EqualsAndHashCode(callSuper = true)
public class ModifierVO extends ModelingBaseVO {

    private String modifierType;
    private String enShortName;
    private String enName;
    private String cnName;
    private String director;
    private String businessCaliber;
    private String remark;
    private DwTypeEnum dwType;
    private String martId;
    private String subjectId;
    private String busTypeId;
    private String domainId;
    private String dimensionTbId;
    private String dimensionFdId;
    private String codeId;
    private String codeDetailId;
    private Boolean submit;
}
