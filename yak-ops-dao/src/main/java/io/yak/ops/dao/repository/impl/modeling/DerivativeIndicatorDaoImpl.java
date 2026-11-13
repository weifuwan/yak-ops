package io.yak.ops.dao.repository.impl.modeling;

import io.yak.ops.dao.entity.DerivativeIndicatorPO;
import io.yak.ops.dao.mapper.DerivativeIndicatorMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.modeling.DerivativeIndicatorDao;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

@Repository
public class DerivativeIndicatorDaoImpl extends BaseDao<DerivativeIndicatorPO, DerivativeIndicatorMapper> implements DerivativeIndicatorDao {

    public DerivativeIndicatorDaoImpl(@NonNull DerivativeIndicatorMapper mapper) {
        super(mapper);
    }
}
