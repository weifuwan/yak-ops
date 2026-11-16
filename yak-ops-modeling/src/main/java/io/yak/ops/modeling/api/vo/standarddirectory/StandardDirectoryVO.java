package io.yak.ops.modeling.api.vo.standarddirectory;

import io.yak.ops.modeling.api.vo.ModelingBaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Typed response model for StandardDirectory. */
@Data
@EqualsAndHashCode(callSuper = true)
public class StandardDirectoryVO extends ModelingBaseVO {

    private String dirName;
    private String dirType;
    private String dirModule;
    private String parentId;
}
