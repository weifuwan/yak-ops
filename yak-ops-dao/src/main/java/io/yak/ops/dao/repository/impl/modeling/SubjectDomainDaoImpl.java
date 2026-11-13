package io.yak.ops.dao.repository.impl.modeling;

import io.yak.ops.dao.entity.SubjectDomainPO;
import io.yak.ops.dao.mapper.SubjectDomainMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.modeling.SubjectDomainDao;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

@Repository
public class SubjectDomainDaoImpl extends BaseDao<SubjectDomainPO, SubjectDomainMapper> implements SubjectDomainDao {

    public SubjectDomainDaoImpl(@NonNull SubjectDomainMapper mapper) {
        super(mapper);
    }
}
