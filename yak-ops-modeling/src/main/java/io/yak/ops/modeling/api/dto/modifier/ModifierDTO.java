package io.yak.ops.modeling.api.dto.modifier;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.yak.ops.modeling.api.dto.pagination.ModelingQueryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import io.yak.ops.dao.entity.enums.DwTypeEnum;

/** Typed request model for Modifier. */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@EqualsAndHashCode(callSuper = true)
public class ModifierDTO extends ModelingQueryDTO {

    private String modifierType;
    private String enShortName;
    private String enName;
    private String cnName;
    private String director;
    private String businessCaliber;
    private String remark;
    private Boolean submit;
    private String dwhId;
    private String martId;
    private String subjectId;
    private String busTypeId;
    private String domainId;
    private String dimensionTbId;
    private String dimensionFdId;
    private String codeId;
    private String codeDetailId;
    private DwTypeEnum dwType;
}
