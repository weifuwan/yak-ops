package io.yak.ops.modeling.api.dto.derivedindicator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.yak.ops.modeling.api.dto.pagination.ModelingQueryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Typed request model for DerivedIndicator. */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@EqualsAndHashCode(callSuper = true)
public class DerivedIndicatorDTO extends ModelingQueryDTO {

    private String timePeriodId;
    private String modifierId;
    private String atomicId;
    private String dwhId;
    private String martId;
    private String subjectId;
    private String busTypeId;
    private String domainId;
    private String busProcessId;
    private String enShortName;
    private String enName;
    private String cnName;
    private String director;
    private String remark;
    private Boolean submit;
}
