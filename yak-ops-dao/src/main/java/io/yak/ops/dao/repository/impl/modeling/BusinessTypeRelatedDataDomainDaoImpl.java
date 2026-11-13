package io.yak.ops.dao.repository.impl.modeling;

import io.yak.ops.dao.entity.BusinessTypeRelatedDataDomainPO;
import io.yak.ops.dao.mapper.BusinessTypeRelatedDataDomainMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.modeling.BusinessTypeRelatedDataDomainDao;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

@Repository
public class BusinessTypeRelatedDataDomainDaoImpl extends BaseDao<BusinessTypeRelatedDataDomainPO, BusinessTypeRelatedDataDomainMapper> implements BusinessTypeRelatedDataDomainDao {

    public BusinessTypeRelatedDataDomainDaoImpl(@NonNull BusinessTypeRelatedDataDomainMapper mapper) {
        super(mapper);
    }
}
