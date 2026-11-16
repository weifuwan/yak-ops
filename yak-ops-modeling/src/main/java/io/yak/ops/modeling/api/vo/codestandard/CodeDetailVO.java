package io.yak.ops.modeling.api.vo.codestandard;

import io.yak.ops.modeling.api.vo.ModelingBaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Typed response model for CodeDetail. */
@Data
@EqualsAndHashCode(callSuper = true)
public class CodeDetailVO extends ModelingBaseVO {

    private String codeId;
    private String enShortName;
    private String enName;
    private String cnName;
    private String remark;
}
