package io.yak.ops.modeling.api.vo.codestandard;

import io.yak.ops.modeling.api.vo.ModelingBaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;
import io.yak.ops.modeling.api.vo.codestandard.CodeDetailVO;

/** Typed response model for CodeStandard. */
@Data
@EqualsAndHashCode(callSuper = true)
public class CodeStandardVO extends ModelingBaseVO {

    private String parentId;
    private String enShortName;
    private String enName;
    private String cnName;
    private String remark;
    private List<CodeDetailVO> detailList;
}
