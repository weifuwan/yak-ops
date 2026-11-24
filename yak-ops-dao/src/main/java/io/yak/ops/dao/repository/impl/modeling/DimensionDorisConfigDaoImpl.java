package io.yak.ops.dao.repository.impl.modeling;

import io.yak.ops.dao.entity.DimensionDorisConfigPO;
import io.yak.ops.dao.mapper.DimensionDorisConfigMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.modeling.DimensionDorisConfigDao;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

@Repository
public class DimensionDorisConfigDaoImpl extends BaseDao<DimensionDorisConfigPO, DimensionDorisConfigMapper> implements DimensionDorisConfigDao {

    public DimensionDorisConfigDaoImpl(@NonNull DimensionDorisConfigMapper mapper) {
        super(mapper);
    }
}
