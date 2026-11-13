package io.yak.ops.dao.repository.impl.modeling;

import io.yak.ops.dao.entity.DataDomainPO;
import io.yak.ops.dao.mapper.DataDomainMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.modeling.DataDomainDao;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

@Repository
public class DataDomainDaoImpl extends BaseDao<DataDomainPO, DataDomainMapper> implements DataDomainDao {

    public DataDomainDaoImpl(@NonNull DataDomainMapper mapper) {
        super(mapper);
    }
}
