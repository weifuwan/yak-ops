package io.yak.ops.modeling.api.dto.standarddirectory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.yak.ops.modeling.api.dto.pagination.ModelingQueryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Typed request model for StandardDirectory. */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@EqualsAndHashCode(callSuper = true)
public class StandardDirectoryDTO extends ModelingQueryDTO {

    private String dirName;
    private String dirType;
    private String dirModule;
    private String parentId;
}
