package io.yak.ops.dao.repository.impl.modeling;

import io.yak.ops.dao.entity.UnitStandardPO;
import io.yak.ops.dao.mapper.UnitStandardMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.modeling.UnitStandardDao;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

@Repository
public class UnitStandardDaoImpl extends BaseDao<UnitStandardPO, UnitStandardMapper> implements UnitStandardDao {

    public UnitStandardDaoImpl(@NonNull UnitStandardMapper mapper) {
        super(mapper);
    }
}
