package io.yak.ops.modeling.api.dto.codestandard;

import io.yak.ops.modeling.api.dto.pagination.ModelingQueryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Typed request model for CodeDetail. */
@Data
@EqualsAndHashCode(callSuper = true)
public class CodeDetailDTO extends ModelingQueryDTO {

    private String codeId;
    private String enShortName;
    private String enName;
    private String cnName;
    private String remark;
}
