package io.yak.ops.dao.repository.impl.modeling;

import io.yak.ops.dao.entity.DimensionDorisDistributionFieldPO;
import io.yak.ops.dao.mapper.DimensionDorisDistributionFieldMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.modeling.DimensionDorisDistributionFieldDao;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

@Repository
public class DimensionDorisDistributionFieldDaoImpl extends BaseDao<DimensionDorisDistributionFieldPO, DimensionDorisDistributionFieldMapper> implements DimensionDorisDistributionFieldDao {

    public DimensionDorisDistributionFieldDaoImpl(@NonNull DimensionDorisDistributionFieldMapper mapper) {
        super(mapper);
    }
}
