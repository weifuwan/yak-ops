package io.yak.ops.modeling.api.dto.codestandard;

import io.yak.ops.modeling.api.dto.pagination.ModelingQueryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;
import io.yak.ops.modeling.api.dto.codestandard.CodeDetailDTO;

/** Typed request model for CodeStandard. */
@Data
@EqualsAndHashCode(callSuper = true)
public class CodeStandardDTO extends ModelingQueryDTO {

    private String parentId;
    private String enShortName;
    private String enName;
    private String cnName;
    private String remark;
    private List<CodeDetailDTO> detailList;
}
