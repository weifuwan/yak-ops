package io.yak.ops.dao.repository.impl.modeling;

import io.yak.ops.dao.entity.AtomIndicatorPO;
import io.yak.ops.dao.mapper.AtomIndicatorMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.modeling.AtomIndicatorDao;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

@Repository
public class AtomIndicatorDaoImpl extends BaseDao<AtomIndicatorPO, AtomIndicatorMapper> implements AtomIndicatorDao {

    public AtomIndicatorDaoImpl(@NonNull AtomIndicatorMapper mapper) {
        super(mapper);
    }
}
