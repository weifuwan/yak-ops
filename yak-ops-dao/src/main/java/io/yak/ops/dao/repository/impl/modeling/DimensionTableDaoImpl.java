package io.yak.ops.dao.repository.impl.modeling;

import io.yak.ops.dao.entity.DimensionTablePO;
import io.yak.ops.dao.mapper.DimensionTableMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.modeling.DimensionTableDao;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

@Repository
public class DimensionTableDaoImpl extends BaseDao<DimensionTablePO, DimensionTableMapper> implements DimensionTableDao {

    public DimensionTableDaoImpl(@NonNull DimensionTableMapper mapper) {
        super(mapper);
    }
}
