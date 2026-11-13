package io.yak.ops.dao.repository.impl.modeling;

import io.yak.ops.dao.entity.BusinessTypePO;
import io.yak.ops.dao.mapper.BusinessTypeMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.modeling.BusinessTypeDao;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

@Repository
public class BusinessTypeDaoImpl extends BaseDao<BusinessTypePO, BusinessTypeMapper> implements BusinessTypeDao {

    public BusinessTypeDaoImpl(@NonNull BusinessTypeMapper mapper) {
        super(mapper);
    }
}
