package io.yak.ops.modeling.application.service;

import io.yak.ops.modeling.api.dto.standarddirectory.StandardDirectoryDTO;
import io.yak.ops.modeling.api.vo.standarddirectory.StandardDirectoryVO;
import java.util.List;
import io.yak.ops.modeling.api.vo.tree.ModelingAssetTreeVO;
import io.yak.ops.modeling.api.dto.pagination.ModelingQueryDTO;
import io.yak.ops.modeling.api.vo.ModelingBaseVO;

/** Typed application contract for StandardDirectory. */
public interface StandardDirectoryService extends ModelingCrudService<StandardDirectoryDTO, StandardDirectoryVO> {

    List<ModelingAssetTreeVO> tree(String module);
}
