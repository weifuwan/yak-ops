package io.yak.ops.dao.repository.impl.modeling;

import io.yak.ops.dao.entity.TimePeriodPO;
import io.yak.ops.dao.mapper.TimePeriodMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.modeling.TimePeriodDao;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

@Repository
public class TimePeriodDaoImpl extends BaseDao<TimePeriodPO, TimePeriodMapper> implements TimePeriodDao {

    public TimePeriodDaoImpl(@NonNull TimePeriodMapper mapper) {
        super(mapper);
    }
}
