package io.yak.ops.modeling.api.dto.namingdictionary;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.yak.ops.modeling.api.dto.pagination.ModelingQueryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Typed request model for NamingDictionary. */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@EqualsAndHashCode(callSuper = true)
public class NamingDictionaryDTO extends ModelingQueryDTO {

    private String enShortName;
    private String enName;
    private String cnName;
}
