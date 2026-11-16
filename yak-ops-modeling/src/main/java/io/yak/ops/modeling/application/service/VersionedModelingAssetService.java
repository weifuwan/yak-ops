package io.yak.ops.modeling.application.service;

import io.yak.ops.modeling.api.dto.pagination.ModelingQueryDTO;
import io.yak.ops.modeling.api.vo.ModelingAssetVersionVO;
import io.yak.ops.modeling.api.vo.ModelingBaseVO;
import java.util.List;

/** CRUD contract for modeling assets that support submit and version rollback. */
public interface VersionedModelingAssetService<D extends ModelingQueryDTO, V extends ModelingBaseVO>
        extends ModelingCrudService<D, V> {

    V submit(String id, String operator);

    List<ModelingAssetVersionVO> listVersions(String id);

    V rollback(String id, Integer versionNumber, String operator);
}
