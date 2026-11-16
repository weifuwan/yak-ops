package io.yak.ops.modeling.api.dto.fieldstandard;

import io.yak.ops.modeling.api.dto.pagination.ModelingQueryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Typed request model for FieldStandard. */
@Data
@EqualsAndHashCode(callSuper = true)
public class FieldStandardDTO extends ModelingQueryDTO {

    private String stdCode;
    private String enShortName;
    private String enName;
    private String cnName;
    private String fieldDataType;
    private Integer fieldLength;
    private Integer fieldPrecision;
    private Boolean fieldCanNull;
    private String fieldDefaultValue;
    private String businessDefinition;
    private String parentStd;
    private String parentId;
    private String reference;
}
