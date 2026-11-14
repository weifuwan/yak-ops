package io.yak.ops.dao.repository.modeling;

import io.yak.ops.dao.entity.DwLayerPO;
import io.yak.ops.dao.repository.IDao;

public interface DwLayerDao extends IDao<DwLayerPO> {

    boolean existsByEnShortName(String enShortName);

    boolean existsSystemOdsLayer();
}
