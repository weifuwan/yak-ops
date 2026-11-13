package io.yak.ops.dao.repository.impl.modeling;

import io.yak.ops.dao.entity.FieldStandardPO;
import io.yak.ops.dao.mapper.FieldStandardMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.modeling.FieldStandardDao;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

@Repository
public class FieldStandardDaoImpl extends BaseDao<FieldStandardPO, FieldStandardMapper> implements FieldStandardDao {

    public FieldStandardDaoImpl(@NonNull FieldStandardMapper mapper) {
        super(mapper);
    }
}
