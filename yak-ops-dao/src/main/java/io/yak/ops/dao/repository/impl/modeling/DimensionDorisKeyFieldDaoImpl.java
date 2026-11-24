package io.yak.ops.dao.repository.impl.modeling;

import io.yak.ops.dao.entity.DimensionDorisKeyFieldPO;
import io.yak.ops.dao.mapper.DimensionDorisKeyFieldMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.modeling.DimensionDorisKeyFieldDao;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

@Repository
public class DimensionDorisKeyFieldDaoImpl extends BaseDao<DimensionDorisKeyFieldPO, DimensionDorisKeyFieldMapper> implements DimensionDorisKeyFieldDao {

    public DimensionDorisKeyFieldDaoImpl(@NonNull DimensionDorisKeyFieldMapper mapper) {
        super(mapper);
    }
}
