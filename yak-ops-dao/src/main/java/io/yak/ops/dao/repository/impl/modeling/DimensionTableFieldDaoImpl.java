package io.yak.ops.dao.repository.impl.modeling;

import io.yak.ops.dao.entity.DimensionTableFieldPO;
import io.yak.ops.dao.mapper.DimensionTableFieldMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.modeling.DimensionTableFieldDao;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

@Repository
public class DimensionTableFieldDaoImpl extends BaseDao<DimensionTableFieldPO, DimensionTableFieldMapper> implements DimensionTableFieldDao {

    public DimensionTableFieldDaoImpl(@NonNull DimensionTableFieldMapper mapper) {
        super(mapper);
    }
}
