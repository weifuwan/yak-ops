package io.yak.ops.dao.repository.impl.modeling;

import io.yak.ops.dao.entity.TimePeriodCategoryPO;
import io.yak.ops.dao.mapper.TimePeriodCategoryMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.modeling.TimePeriodCategoryDao;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

@Repository
public class TimePeriodCategoryDaoImpl extends BaseDao<TimePeriodCategoryPO, TimePeriodCategoryMapper> implements TimePeriodCategoryDao {

    public TimePeriodCategoryDaoImpl(@NonNull TimePeriodCategoryMapper mapper) {
        super(mapper);
    }
}
