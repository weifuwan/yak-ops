package io.yak.ops.modeling.api.dto.atomindicator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.yak.ops.modeling.api.dto.pagination.ModelingQueryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import io.yak.ops.dao.entity.enums.DwTypeEnum;

/** Typed request model for AtomIndicator. */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@EqualsAndHashCode(callSuper = true)
public class AtomIndicatorDTO extends ModelingQueryDTO {

    private String enShortName;
    private String enName;
    private String cnName;
    private String director;
    private String remark;
    private String businessCaliber;
    private String busTypeId;
    private String domainId;
    private String busProcessId;
    private DwTypeEnum dwType;
    private String calculateFunction;
    private Integer decimalNumber;
    private String unitId;
    private Boolean removeDuplicates;
    private Boolean submit;
}
