package io.yak.ops.modeling.api.vo.fieldstandard;

import io.yak.ops.modeling.api.vo.ModelingBaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Typed response model for FieldStandard. */
@Data
@EqualsAndHashCode(callSuper = true)
public class FieldStandardVO extends ModelingBaseVO {

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
