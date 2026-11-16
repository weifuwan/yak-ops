package io.yak.ops.modeling.api.vo.namingdictionary;

import io.yak.ops.modeling.api.vo.ModelingBaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Typed response model for NamingDictionary. */
@Data
@EqualsAndHashCode(callSuper = true)
public class NamingDictionaryVO extends ModelingBaseVO {

    private String enShortName;
    private String enName;
    private String cnName;
}
