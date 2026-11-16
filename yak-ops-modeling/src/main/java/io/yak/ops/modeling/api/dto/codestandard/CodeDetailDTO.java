package io.yak.ops.modeling.api.dto.codestandard;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.yak.ops.modeling.api.dto.pagination.ModelingQueryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Typed request model for CodeDetail. */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@EqualsAndHashCode(callSuper = true)
public class CodeDetailDTO extends ModelingQueryDTO {

    private String codeId;
    private String enShortName;
    private String enName;
    private String cnName;
    private String remark;
}
