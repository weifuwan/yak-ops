package io.yak.ops.dao.repository.impl.modeling;

import io.yak.ops.dao.entity.DwLayerPO;
import io.yak.ops.dao.mapper.DwLayerMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.modeling.DwLayerDao;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

@Repository
public class DwLayerDaoImpl extends BaseDao<DwLayerPO, DwLayerMapper> implements DwLayerDao {

    public DwLayerDaoImpl(@NonNull DwLayerMapper mapper) {
        super(mapper);
    }
}
