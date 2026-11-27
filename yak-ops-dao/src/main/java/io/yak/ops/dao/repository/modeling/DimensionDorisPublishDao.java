package io.yak.ops.dao.repository.modeling;

import io.yak.ops.dao.entity.DimensionDorisPublishPO;
import io.yak.ops.dao.repository.IDao;

public interface DimensionDorisPublishDao extends IDao<DimensionDorisPublishPO> {

    /**
     * Query the latest successful publish baseline within the active project.
     */
    DimensionDorisPublishPO queryLatestSuccessful(
            String tableId, Integer datasourceId, String database, String table);
}
